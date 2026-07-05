/*
 * Function: Role service — CRUD, permission assignment with audit and outbox events
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.error.ErrorCode;
import io.opengeobot.platform.common.error.PlatformException;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.id.Ulid;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.iam.domain.Permission;
import io.opengeobot.platform.iam.domain.Role;
import io.opengeobot.platform.iam.domain.RolePermission;
import io.opengeobot.platform.iam.domain.UserRole;
import io.opengeobot.platform.iam.dto.CreateRoleRequest;
import io.opengeobot.platform.iam.dto.PermissionDto;
import io.opengeobot.platform.iam.dto.RoleDto;
import io.opengeobot.platform.iam.dto.UpdateRoleRequest;
import io.opengeobot.platform.iam.repository.PermissionRepository;
import io.opengeobot.platform.iam.repository.RolePermissionRepository;
import io.opengeobot.platform.iam.repository.RoleRepository;
import io.opengeobot.platform.iam.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application service for role management. Handles creation, update, detail
 * retrieval, permission assignment and permission listing. Each mutation
 * writes an audit event and an outbox event in the same transaction.
 */
@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private static final String EVENT_ROLE_CHANGED = "iam.role_changed.v1";
    private static final String EVENT_AUTHORIZATION_CHANGED = "iam.authorization_changed.v1";
    private static final String AGGREGATE_ROLE = "role";
    private static final String AGGREGATE_USER = "user";
    private static final String ROLE_ID_PREFIX = "rol";

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;
    private final OutboxRepository outboxRepository;
    private final PublicIdGenerator idGenerator;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;
    private final PermissionCache permissionCache;

    public RoleService(RoleRepository roleRepository,
                       RolePermissionRepository rolePermissionRepository,
                       PermissionRepository permissionRepository,
                       UserRoleRepository userRoleRepository,
                       AuditService auditService,
                       OutboxRepository outboxRepository,
                       PublicIdGenerator idGenerator,
                       ClockProvider clockProvider,
                       ObjectMapper objectMapper,
                       PermissionCache permissionCache) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
        this.outboxRepository = outboxRepository;
        this.idGenerator = idGenerator;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
        this.permissionCache = permissionCache;
    }

    @Transactional(readOnly = true)
    public PageResult<RoleDto> list(PageRequest pageRequest) {
        Page<Role> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        IPage<Role> result = roleRepository.selectPage(page, null);
        List<RoleDto> items = result.getRecords().stream()
                .map(role -> toDto(role, loadPermissionCodes(role.getRoleId())))
                .toList();
        return new PageResult<>(items, result.getTotal(), pageRequest.pageNumber(), pageRequest.pageSize());
    }

    @Transactional(readOnly = true)
    public RoleDto getByRoleId(String roleId) {
        Role role = findRoleOrThrow(roleId);
        return toDto(role, loadPermissionCodes(roleId));
    }

    @Transactional
    public RoleDto create(CreateRoleRequest request, String actorUserId) {
        String traceId = resolveTraceId();

        if (roleRepository.findByRoleCode(request.roleCode()) != null) {
            throw new PlatformException(ErrorCode.CONFLICT, "Role code already exists: " + request.roleCode());
        }

        OffsetDateTime now = OffsetDateTime.now(clockProvider.getClock());
        Role role = new Role();
        role.setRoleId(idGenerator.generate(ROLE_ID_PREFIX));
        role.setRoleName(request.roleName());
        role.setRoleCode(request.roleCode());
        role.setDescription(request.description());
        role.setStatus("ACTIVE");
        role.setSortOrder(0);
        role.setBuiltIn(false);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setCreatedBy(actorUserId);
        role.setUpdatedBy(actorUserId);
        roleRepository.insert(role);

        recordAudit(actorUserId, "CREATE_ROLE", role.getRoleId(), "SUCCESS", null, traceId);
        publishOutboxEvent(EVENT_ROLE_CHANGED, AGGREGATE_ROLE, role.getRoleId(),
                buildRoleChangedPayload(role.getRoleId(), "CREATED", traceId), traceId);

        log.info("Role created: roleId={} roleCode={}", role.getRoleId(), role.getRoleCode());
        return toDto(role, List.of());
    }

    @Transactional
    public RoleDto update(String roleId, UpdateRoleRequest request, String actorUserId) {
        String traceId = resolveTraceId();
        Role role = findRoleOrThrow(roleId);

        if (request.roleName() != null) {
            role.setRoleName(request.roleName());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        if (request.status() != null) {
            role.setStatus(request.status());
        }
        role.setUpdatedAt(OffsetDateTime.now(clockProvider.getClock()));
        role.setUpdatedBy(actorUserId);
        roleRepository.updateById(role);

        recordAudit(actorUserId, "UPDATE_ROLE", roleId, "SUCCESS", null, traceId);
        publishOutboxEvent(EVENT_ROLE_CHANGED, AGGREGATE_ROLE, roleId,
                buildRoleChangedPayload(roleId, "UPDATED", traceId), traceId);

        log.info("Role updated: roleId={}", roleId);
        return toDto(role, loadPermissionCodes(roleId));
    }

    @Transactional
    public void assignPermissions(String roleId, List<String> permissionCodes, String actorUserId) {
        String traceId = resolveTraceId();
        Role role = findRoleOrThrow(roleId);

        rolePermissionRepository.deleteByRoleId(roleId);

        if (permissionCodes != null) {
            OffsetDateTime now = OffsetDateTime.now(clockProvider.getClock());
            for (String code : permissionCodes) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionCode(code);
                rp.setCreatedAt(now);
                rp.setCreatedBy(actorUserId);
                rolePermissionRepository.insert(rp);
            }
        }

        recordAudit(actorUserId, "ASSIGN_ROLE_PERMISSIONS", roleId, "SUCCESS", null, traceId);
        publishOutboxEvent(EVENT_ROLE_CHANGED, AGGREGATE_ROLE, roleId,
                buildRoleChangedPayload(roleId, "PERMISSIONS_UPDATED", traceId), traceId);

        List<UserRole> userRoles = userRoleRepository.findByRoleId(roleId);
        for (UserRole userRole : userRoles) {
            String userId = userRole.getUserId();
            publishOutboxEvent(EVENT_AUTHORIZATION_CHANGED, AGGREGATE_USER, userId,
                    buildAuthorizationChangedPayload(userId, "ROLE_PERMISSIONS_CHANGED", traceId), traceId);
            permissionCache.invalidate(userId);
        }

        log.info("Permissions assigned to role: roleId={} count={} affectedUsers={}",
                roleId, permissionCodes != null ? permissionCodes.size() : 0, userRoles.size());
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> getRolePermissions(String roleId) {
        findRoleOrThrow(roleId);
        return permissionRepository.findByRoleId(roleId).stream()
                .map(RoleService::toPermissionDto)
                .toList();
    }

    private Role findRoleOrThrow(String roleId) {
        Role role = roleRepository.findByRoleId(roleId);
        if (role == null) {
            throw new PlatformException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found: " + roleId);
        }
        return role;
    }

    private List<String> loadPermissionCodes(String roleId) {
        return permissionRepository.findPermissionCodesByRoleId(roleId);
    }

    private void recordAudit(String actorId, String action, String resourceId,
                             String result, String reasonCode, String traceId) {
        auditService.record(new AuditEvent(
                "user", actorId, action, "role", resourceId, result,
                reasonCode, null, null, traceId, null,
                Instant.now(clockProvider.getClock()), null, null
        ));
    }

    private void publishOutboxEvent(String eventType, String aggregateType, String aggregateId,
                                   String payload, String traceId) {
        outboxRepository.save(new OutboxEvent(
                null, Ulid.next(), eventType, "1",
                aggregateType, aggregateId, 1L,
                payload, Instant.now(clockProvider.getClock()), traceId,
                false, null, 0
        ));
    }

    private String buildRoleChangedPayload(String roleId, String action, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_id", Ulid.next());
        data.put("role_id", roleId);
        data.put("action", action);
        data.put("occurred_at", Instant.now(clockProvider.getClock()).toString());
        data.put("trace_id", traceId);
        return toJson(data);
    }

    private String buildAuthorizationChangedPayload(String userId, String changeType, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_id", Ulid.next());
        data.put("user_id", userId);
        data.put("change_type", changeType);
        data.put("occurred_at", Instant.now(clockProvider.getClock()).toString());
        data.put("trace_id", traceId);
        return toJson(data);
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload", e);
            throw new PlatformException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = Ulid.next();
            MDC.put("traceId", traceId);
        }
        return traceId;
    }

    private static RoleDto toDto(Role role, List<String> permissionCodes) {
        return new RoleDto(
                role.getRoleId(),
                role.getRoleName(),
                role.getRoleCode(),
                role.getDescription(),
                role.getStatus(),
                role.getSortOrder(),
                role.getBuiltIn(),
                permissionCodes,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }

    private static PermissionDto toPermissionDto(Permission permission) {
        return new PermissionDto(
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getModule(),
                permission.getDescription(),
                permission.getResourceType(),
                permission.getAction()
        );
    }
}
