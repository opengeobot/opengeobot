/*
 * Function: AuditService implementation — persists audit events to sys_operation_audit
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.governance.audit.repository.AuditRepository;
import io.opengeobot.platform.governance.domain.audit.OperationAudit;
import io.opengeobot.platform.governance.dto.AuditLogDto;
import io.opengeobot.platform.governance.dto.AuditQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Implementation of the platform-common {@link io.opengeobot.platform.common.audit.AuditService}
 * interface, persisting audit events to the append-only
 * {@code platform_governance.sys_operation_audit} table. Also provides a query
 * method for the audit API.
 */
@Service("governanceAuditServiceImpl")
@Primary
public class AuditServiceImpl implements io.opengeobot.platform.common.audit.AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);
    private static final String AUDIT_PREFIX = "audit";

    private final AuditRepository auditRepository;
    private final PublicIdGenerator idGenerator;
    private final ClockProvider clockProvider;

    public AuditServiceImpl(AuditRepository auditRepository,
                            PublicIdGenerator idGenerator,
                            ClockProvider clockProvider) {
        this.auditRepository = auditRepository;
        this.idGenerator = idGenerator;
        this.clockProvider = clockProvider;
    }

    @Override
    @Transactional
    public void record(AuditEvent event) {
        OperationAudit entity = new OperationAudit();
        entity.setAuditId(idGenerator.generate(AUDIT_PREFIX));
        entity.setOccurredAt(OffsetDateTime.ofInstant(
                event.occurredAt() != null ? event.occurredAt() : java.time.Instant.now(clockProvider.getClock()),
                ZoneOffset.UTC));
        entity.setActorType(event.actorType());
        entity.setActorId(event.actorId());
        entity.setAction(event.action());
        entity.setResourceType(event.resourceType());
        entity.setResourceId(event.resourceId());
        entity.setResult(event.result());
        entity.setReasonCode(event.reasonCode());
        entity.setSourceIp(event.sourceIp());
        entity.setUserAgent(event.userAgent());
        entity.setTraceId(event.traceId());
        entity.setRequestId(event.requestId());
        entity.setPayloadBefore(event.payloadBefore());
        entity.setPayloadAfter(event.payloadAfter());
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        auditRepository.insert(entity);
        log.debug("Recorded audit {} action={} resource={}/{} result={}",
                entity.getAuditId(), event.action(), event.resourceType(), event.resourceId(), event.result());
    }

    /**
     * Queries audit records with optional filters, ordered by occurred_at
     * descending (most recent first).
     */
    public PageResult<AuditLogDto> query(AuditQueryRequest query, PageRequest pageRequest) {
        LambdaQueryWrapper<OperationAudit> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.eq(query.actorId() != null, OperationAudit::getActorId, query.actorId())
                    .eq(query.action() != null, OperationAudit::getAction, query.action())
                    .eq(query.resourceType() != null, OperationAudit::getResourceType, query.resourceType())
                    .eq(query.resourceId() != null, OperationAudit::getResourceId, query.resourceId())
                    .eq(query.traceId() != null, OperationAudit::getTraceId, query.traceId())
                    .ge(query.occurredFrom() != null, OperationAudit::getOccurredAt, query.occurredFrom())
                    .le(query.occurredTo() != null, OperationAudit::getOccurredAt, query.occurredTo());
        }
        wrapper.orderByDesc(OperationAudit::getOccurredAt);

        Page<OperationAudit> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<OperationAudit> result = auditRepository.selectPage(page, wrapper);

        return new PageResult<>(
                result.getRecords().stream().map(AuditServiceImpl::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    private static AuditLogDto toDto(OperationAudit entity) {
        return new AuditLogDto(
                entity.getAuditId(),
                entity.getOccurredAt(),
                entity.getActorType(),
                entity.getActorId(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getResult(),
                entity.getReasonCode(),
                entity.getReasonDetail(),
                entity.getTraceId(),
                entity.getSourceIp()
        );
    }
}
