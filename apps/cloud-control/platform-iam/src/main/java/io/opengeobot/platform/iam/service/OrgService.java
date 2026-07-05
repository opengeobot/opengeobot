/*
 * Function: Organization service — tree CRUD with path management
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

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
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.iam.domain.Org;
import io.opengeobot.platform.iam.domain.User;
import io.opengeobot.platform.iam.domain.UserOrg;
import io.opengeobot.platform.iam.dto.CreateOrgRequest;
import io.opengeobot.platform.iam.dto.OrgDto;
import io.opengeobot.platform.iam.dto.OrgTreeNodeDto;
import io.opengeobot.platform.iam.dto.UpdateOrgRequest;
import io.opengeobot.platform.iam.dto.UserDto;
import io.opengeobot.platform.iam.repository.OrgRepository;
import io.opengeobot.platform.iam.repository.UserOrgRepository;
import io.opengeobot.platform.iam.repository.UserRepository;
import io.opengeobot.platform.iam.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Application service for organization management. Handles tree-structured CRUD
 * with materialized path maintenance, and user lookup by organization. Each
 * mutation writes an audit event.
 */
@Service
public class OrgService {

    private static final Logger log = LoggerFactory.getLogger(OrgService.class);

    private static final String EVENT_ORG_CREATED = "iam.org_created.v1";
    private static final String EVENT_ORG_UPDATED = "iam.org_updated.v1";
    private static final String EVENT_ORG_DELETED = "iam.org_deleted.v1";
    private static final String AGGREGATE_ORG = "org";
    private static final String ORG_ID_PREFIX = "org";

    private final OrgRepository orgRepository;
    private final UserOrgRepository userOrgRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;
    private final OutboxRepository outboxRepository;
    private final PublicIdGenerator idGenerator;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;

    public OrgService(OrgRepository orgRepository,
                      UserOrgRepository userOrgRepository,
                      UserRepository userRepository,
                      UserRoleRepository userRoleRepository,
                      AuditService auditService,
                      OutboxRepository outboxRepository,
                      PublicIdGenerator idGenerator,
                      ClockProvider clockProvider,
                      ObjectMapper objectMapper) {
        this.orgRepository = orgRepository;
        this.userOrgRepository = userOrgRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
        this.outboxRepository = outboxRepository;
        this.idGenerator = idGenerator;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<OrgTreeNodeDto> list() {
        List<Org> allOrgs = orgRepository.selectList(null);
        return buildTree(allOrgs);
    }

    @Transactional(readOnly = true)
    public OrgDto getByOrgId(String orgId) {
        Org org = findOrgOrThrow(orgId);
        return toDto(org);
    }

    @Transactional
    public OrgDto create(CreateOrgRequest request, String actorUserId) {
        String traceId = resolveTraceId();

        if (orgRepository.findByOrgCode(request.orgCode()) != null) {
            throw new PlatformException(ErrorCode.CONFLICT, "Org code already exists: " + request.orgCode());
        }

        OffsetDateTime now = OffsetDateTime.now(clockProvider.getClock());
        Org org = new Org();
        org.setOrgId(idGenerator.generate(ORG_ID_PREFIX));
        org.setOrgName(request.orgName());
        org.setOrgCode(request.orgCode());
        org.setDescription(request.description());
        org.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        org.setStatus("ACTIVE");
        org.setCreatedAt(now);
        org.setUpdatedAt(now);
        org.setCreatedBy(actorUserId);
        org.setUpdatedBy(actorUserId);

        String parentId = request.parentId();
        if (parentId != null && !parentId.isBlank()) {
            Org parent = findOrgOrThrow(parentId);
            org.setParentId(parentId);
            org.setPath(parent.getPath() + "/" + org.getOrgId());
        } else {
            org.setPath("/" + org.getOrgId());
        }

        orgRepository.insert(org);

        recordAudit(actorUserId, "CREATE_ORG", org.getOrgId(), "SUCCESS", null, traceId);
        publishOutboxEvent(EVENT_ORG_CREATED, AGGREGATE_ORG, org.getOrgId(),
                buildOrgPayload(org.getOrgId(), traceId), traceId);

        log.info("Org created: orgId={} orgCode={}", org.getOrgId(), org.getOrgCode());
        return toDto(org);
    }

    @Transactional
    public OrgDto update(String orgId, UpdateOrgRequest request, String actorUserId) {
        String traceId = resolveTraceId();
        Org org = findOrgOrThrow(orgId);

        if (request.orgName() != null) {
            org.setOrgName(request.orgName());
        }
        if (request.sortOrder() != null) {
            org.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            org.setStatus(request.status());
        }
        if (request.description() != null) {
            org.setDescription(request.description());
        }
        org.setUpdatedAt(OffsetDateTime.now(clockProvider.getClock()));
        org.setUpdatedBy(actorUserId);
        orgRepository.updateById(org);

        recordAudit(actorUserId, "UPDATE_ORG", orgId, "SUCCESS", null, traceId);
        publishOutboxEvent(EVENT_ORG_UPDATED, AGGREGATE_ORG, orgId,
                buildOrgPayload(orgId, traceId), traceId);

        log.info("Org updated: orgId={}", orgId);
        return toDto(org);
    }

    @Transactional
    public void delete(String orgId, String actorUserId) {
        String traceId = resolveTraceId();
        Org org = findOrgOrThrow(orgId);

        List<Org> children = orgRepository.findByParentId(orgId);
        if (!children.isEmpty()) {
            throw new PlatformException(ErrorCode.CONFLICT, "Cannot delete org with children");
        }

        orgRepository.deleteById(org.getId());

        recordAudit(actorUserId, "DELETE_ORG", orgId, "SUCCESS", null, traceId);
        publishOutboxEvent(EVENT_ORG_DELETED, AGGREGATE_ORG, orgId,
                buildOrgPayload(orgId, traceId), traceId);

        log.info("Org deleted: orgId={}", orgId);
    }

    @Transactional(readOnly = true)
    public List<UserDto> getUsersByOrg(String orgId) {
        findOrgOrThrow(orgId);
        List<UserOrg> userOrgs = userOrgRepository.findByOrgId(orgId);
        if (userOrgs.isEmpty()) {
            return List.of();
        }

        String orgName = orgRepository.findByOrgId(orgId).getOrgName();
        return userOrgs.stream()
                .map(uo -> {
                    User user = userRepository.findByUserId(uo.getUserId());
                    if (user == null) {
                        return null;
                    }
                    List<String> roleIds = userRoleRepository.findByUserId(user.getUserId()).stream()
                            .map(ur -> ur.getRoleId())
                            .toList();
                    return toUserDto(user, orgId, orgName, roleIds);
                })
                .filter(dto -> dto != null)
                .toList();
    }

    private Org findOrgOrThrow(String orgId) {
        Org org = orgRepository.findByOrgId(orgId);
        if (org == null) {
            throw new PlatformException(ErrorCode.RESOURCE_NOT_FOUND, "Org not found: " + orgId);
        }
        return org;
    }

    private List<OrgTreeNodeDto> buildTree(List<Org> allOrgs) {
        Map<String, List<Org>> byParent = allOrgs.stream()
                .collect(Collectors.groupingBy(
                        org -> org.getParentId() != null ? org.getParentId() : "ROOT"));

        List<Org> roots = allOrgs.stream()
                .filter(org -> org.getParentId() == null)
                .toList();

        return roots.stream()
                .map(org -> toTreeNode(org, byParent))
                .toList();
    }

    private OrgTreeNodeDto toTreeNode(Org org, Map<String, List<Org>> byParent) {
        List<Org> children = byParent.getOrDefault(org.getOrgId(), List.of());
        List<OrgTreeNodeDto> childNodes = children.stream()
                .map(child -> toTreeNode(child, byParent))
                .toList();

        return new OrgTreeNodeDto(
                org.getOrgId(),
                org.getOrgName(),
                org.getOrgCode(),
                org.getParentId(),
                org.getDescription(),
                org.getSortOrder(),
                org.getStatus(),
                childNodes
        );
    }

    private void recordAudit(String actorId, String action, String resourceId,
                             String result, String reasonCode, String traceId) {
        auditService.record(new AuditEvent(
                "user", actorId, action, "org", resourceId, result,
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

    private String buildOrgPayload(String orgId, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_id", Ulid.next());
        data.put("org_id", orgId);
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

    private static OrgDto toDto(Org org) {
        return new OrgDto(
                org.getOrgId(),
                org.getOrgName(),
                org.getOrgCode(),
                org.getParentId(),
                org.getDescription(),
                org.getSortOrder(),
                org.getStatus(),
                org.getPath(),
                org.getCreatedAt(),
                org.getUpdatedAt()
        );
    }

    private static UserDto toUserDto(User user, String orgId, String orgName, List<String> roleIds) {
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
}
