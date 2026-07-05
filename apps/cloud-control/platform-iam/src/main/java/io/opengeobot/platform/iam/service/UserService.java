/*
 * Function: User service — CRUD, role assignment with audit and outbox events
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import io.opengeobot.platform.iam.domain.Org;
import io.opengeobot.platform.iam.domain.Role;
import io.opengeobot.platform.iam.domain.User;
import io.opengeobot.platform.iam.domain.UserOrg;
import io.opengeobot.platform.iam.domain.UserRole;
import io.opengeobot.platform.iam.dto.CreateUserRequest;
import io.opengeobot.platform.iam.dto.RoleDto;
import io.opengeobot.platform.iam.dto.UpdateProfileRequest;
import io.opengeobot.platform.iam.dto.UserDto;
import io.opengeobot.platform.iam.repository.OrgRepository;
import io.opengeobot.platform.iam.repository.RoleRepository;
import io.opengeobot.platform.iam.repository.UserOrgRepository;
import io.opengeobot.platform.iam.repository.UserRepository;
import io.opengeobot.platform.iam.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application service for user management. Handles listing, creation, update,
 * status transition and role assignment. Each mutation writes an audit event
 * and an outbox event in the same transaction, linked by a trace_id.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String EVENT_USER_CREATED = "iam.user_created.v1";
    private static final String EVENT_USER_STATUS_CHANGED = "iam.user_status_changed.v1";
    private static final String EVENT_USER_ROLES_CHANGED = "iam.user_roles_changed.v1";
    private static final String EVENT_AUTHORIZATION_CHANGED = "iam.authorization_changed.v1";
    private static final String AGGREGATE_USER = "user";
    private static final String USER_ID_PREFIX = "usr";

    private final UserRepository userRepository;
    private final UserOrgRepository userOrgRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final OrgRepository orgRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final OutboxRepository outboxRepository;
    private final PublicIdGenerator idGenerator;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;
    private final PermissionCache permissionCache;

    public UserService(UserRepository userRepository,
                       UserOrgRepository userOrgRepository,
                       UserRoleRepository userRoleRepository,
                       RoleRepository roleRepository,
                       OrgRepository orgRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService,
                       OutboxRepository outboxRepository,
                       PublicIdGenerator idGenerator,
                       ClockProvider clockProvider,
                       ObjectMapper objectMapper,
                       PermissionCache permissionCache) {
        this.userRepository = userRepository;
        this.userOrgRepository = userOrgRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.orgRepository = orgRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.outboxRepository = outboxRepository;
        this.idGenerator = idGenerator;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
        this.permissionCache = permissionCache;
    }

    @Transactional(readOnly = true)
    public PageResult<UserDto> list(PageRequest pageRequest, String keyword, String orgId, String status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword + "%";
            wrapper.and(w -> w.like(User::getUsername, pattern)
                    .or().like(User::getDisplayName, pattern));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(User::getStatus, status);
        }
        if (orgId != null && !orgId.isBlank()) {
            wrapper.eq(User::getPrimaryOrgId, orgId);
        }

        wrapper.orderByAsc(User::getCreatedAt);

        Page<User> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        IPage<User> result = userRepository.selectPage(page, wrapper);

        List<UserDto> items = result.getRecords().stream()
                .map(this::toUserDto)
                .toList();

        return new PageResult<>(items, result.getTotal(), pageRequest.pageNumber(), pageRequest.pageSize());
    }

    @Transactional(readOnly = true)
    public UserDto getByUserId(String userId) {
        User user = findUserOrThrow(userId);
        return toUserDto(user);
    }

    @Transactional
    public UserDto create(CreateUserRequest request, String actorUserId) {
        String traceId = resolveTraceId();

        if (userRepository.findByUsername(request.username()) != null) {
            throw new PlatformException(ErrorCode.CONFLICT, "Username already exists: " + request.username());
        }

        OffsetDateTime now = OffsetDateTime.now(clockProvider.getClock());
        String userId = idGenerator.generate(USER_ID_PREFIX);

        User user = new User();
        user.setUserId(userId);
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus("ACTIVE");
        user.setFailedLoginCount(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setCreatedBy(actorUserId);
        user.setUpdatedBy(actorUserId);

        List<String> assignedRoleIds = List.of();
        if (request.roleIds() != null && !request.roleIds().isEmpty()) {
            assignedRoleIds = request.roleIds();
        }

        if (request.orgId() != null && !request.orgId().isBlank()) {
            Org org = orgRepository.findByOrgId(request.orgId());
            if (org == null) {
                throw new PlatformException(ErrorCode.RESOURCE_NOT_FOUND, "Org not found: " + request.orgId());
            }
            user.setPrimaryOrgId(request.orgId());

            UserOrg userOrg = new UserOrg();
            userOrg.setUserId(userId);
            userOrg.setOrgId(request.orgId());
            userOrg.setIsPrimary(true);
            userOrg.setCreatedAt(now);
            userOrgRepository.insert(userOrg);
        }

        userRepository.insert(user);

        for (String roleId : assignedRoleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setAssignedAt(now);
            userRole.setAssignedBy(actorUserId);
            userRoleRepository.insert(userRole);
        }

        recordAudit(actorUserId, "CREATE_USER", userId, "SUCCESS", null, traceId);
        publishOutboxEvent(EVENT_USER_CREATED, AGGREGATE_USER, userId,
                buildUserCreatedPayload(userId, request.username(), request.orgId(),
                        assignedRoleIds, actorUserId, traceId), traceId);

        log.info("User created: userId={} username={}", userId, request.username());

        User created = userRepository.findByUserId(userId);
        return toUserDto(created);
    }

    @Transactional
    public UserDto update(String userId, UpdateProfileRequest request, String actorUserId) {
        String traceId = resolveTraceId();
        User user = findUserOrThrow(userId);

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        user.setUpdatedAt(OffsetDateTime.now(clockProvider.getClock()));
        user.setUpdatedBy(actorUserId);
        userRepository.updateById(user);

        recordAudit(actorUserId, "UPDATE_USER", userId, "SUCCESS", null, traceId);

        log.info("User updated: userId={}", userId);
        return toUserDto(user);
    }

    @Transactional
    public void updateStatus(String userId, String status, String reason, String actorUserId) {
        String traceId = resolveTraceId();
        User user = findUserOrThrow(userId);

        String oldStatus = user.getStatus();
        user.setStatus(status);
        user.setUpdatedAt(OffsetDateTime.now(clockProvider.getClock()));
        user.setUpdatedBy(actorUserId);
        userRepository.updateById(user);

        recordAudit(actorUserId, "UPDATE_USER_STATUS", userId, "SUCCESS", reason, traceId);
        publishOutboxEvent(EVENT_USER_STATUS_CHANGED, AGGREGATE_USER, userId,
                buildStatusChangedPayload(userId, oldStatus, status, reason, traceId), traceId);

        log.info("User status changed: userId={} {} -> {}", userId, oldStatus, status);
    }

    @Transactional
    public void assignRoles(String userId, List<String> roleIds, String actorUserId) {
        String traceId = resolveTraceId();
        User user = findUserOrThrow(userId);

        List<UserRole> oldAssignments = userRoleRepository.findByUserId(userId);
        List<String> oldRoleIds = oldAssignments.stream()
                .map(UserRole::getRoleId)
                .toList();

        userRoleRepository.deleteByUserId(userId);

        if (roleIds != null) {
            OffsetDateTime now = OffsetDateTime.now(clockProvider.getClock());
            for (String roleId : roleIds) {
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRole.setAssignedAt(now);
                userRole.setAssignedBy(actorUserId);
                userRoleRepository.insert(userRole);
            }
        }

        List<String> newRoleIds = roleIds != null ? roleIds : List.of();

        recordAudit(actorUserId, "ASSIGN_USER_ROLES", userId, "SUCCESS", null, traceId);
        publishOutboxEvent(EVENT_USER_ROLES_CHANGED, AGGREGATE_USER, userId,
                buildRolesChangedPayload(userId, oldRoleIds, newRoleIds, traceId), traceId);
        publishOutboxEvent(EVENT_AUTHORIZATION_CHANGED, AGGREGATE_USER, userId,
                buildAuthorizationChangedPayload(userId, "USER_ROLES_CHANGED", traceId), traceId);

        permissionCache.invalidate(userId);

        log.info("User roles assigned: userId={} count={}", userId, newRoleIds.size());
    }

    @Transactional(readOnly = true)
    public List<RoleDto> getUserRoles(String userId) {
        findUserOrThrow(userId);
        List<Role> roles = roleRepository.findByUserId(userId);
        return roles.stream()
                .map(role -> new RoleDto(
                        role.getRoleId(),
                        role.getRoleName(),
                        role.getRoleCode(),
                        role.getDescription(),
                        role.getStatus(),
                        role.getSortOrder(),
                        role.getBuiltIn(),
                        null,
                        role.getCreatedAt(),
                        role.getUpdatedAt()))
                .toList();
    }

    private User findUserOrThrow(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new PlatformException(ErrorCode.RESOURCE_NOT_FOUND, "User not found: " + userId);
        }
        return user;
    }

    private UserDto toUserDto(User user) {
        String orgId = user.getPrimaryOrgId();
        String orgName = null;

        if (orgId != null) {
            Org org = orgRepository.findByOrgId(orgId);
            if (org != null) {
                orgName = org.getOrgName();
            }
        }

        List<String> roleIds = userRoleRepository.findByUserId(user.getUserId()).stream()
                .map(UserRole::getRoleId)
                .toList();

        return new UserDto(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getStatus(),
                orgId,
                orgName,
                roleIds,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private void recordAudit(String actorId, String action, String resourceId,
                             String result, String reasonCode, String traceId) {
        auditService.record(new AuditEvent(
                "user", actorId, action, "user", resourceId, result,
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

    private String buildUserCreatedPayload(String userId, String username, String orgId,
                                            List<String> roleIds, String createdBy, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_id", Ulid.next());
        data.put("user_id", userId);
        data.put("username", username);
        data.put("org_ids", orgId != null ? List.of(orgId) : List.of());
        data.put("role_ids", roleIds);
        data.put("created_by", createdBy);
        data.put("occurred_at", Instant.now(clockProvider.getClock()).toString());
        data.put("trace_id", traceId);
        return toJson(data);
    }

    private String buildStatusChangedPayload(String userId, String oldStatus, String newStatus,
                                             String reason, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_id", Ulid.next());
        data.put("user_id", userId);
        data.put("old_status", oldStatus);
        data.put("new_status", newStatus);
        if (reason != null) {
            data.put("reason", reason);
        }
        data.put("occurred_at", Instant.now(clockProvider.getClock()).toString());
        data.put("trace_id", traceId);
        return toJson(data);
    }

    private String buildRolesChangedPayload(String userId, List<String> oldRoleIds,
                                            List<String> newRoleIds, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_id", Ulid.next());
        data.put("user_id", userId);
        data.put("old_role_ids", oldRoleIds);
        data.put("new_role_ids", newRoleIds);
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
}
