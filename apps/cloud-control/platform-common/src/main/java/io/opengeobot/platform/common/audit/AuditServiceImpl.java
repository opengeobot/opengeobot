/*
 * Function: Audit service implementation — persists audit events to platform_governance.sys_operation_audit
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.common.audit;

import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.repository.AuditEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Default {@link AuditService} backed by {@link AuditEventMapper}. Generates a
 * stable audit_id for each event and maps the {@link AuditEvent} record to the
 * wider {@link AuditEventEntity}. Audit records are append-only and persisted
 * in the same transaction as the domain change.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private static final String AUDIT_ID_PREFIX = "aud";
    private static final String DEFAULT_RESULT = "SUCCESS";

    private final AuditEventMapper mapper;
    private final PublicIdGenerator idGenerator;

    public AuditServiceImpl(AuditEventMapper mapper, PublicIdGenerator idGenerator) {
        this.mapper = mapper;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public void record(AuditEvent event) {
        AuditEventEntity entity = toEntity(event);
        mapper.insert(entity);
        log.debug("Recorded audit event action={} resourceType={} resourceId={} result={}",
                event.action(), event.resourceType(), event.resourceId(), event.result());
    }

    private AuditEventEntity toEntity(AuditEvent event) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setAuditId(idGenerator.generate(AUDIT_ID_PREFIX));
        entity.setOccurredAt(toOffsetDateTime(event.occurredAt()));
        entity.setActorType(event.actorType());
        entity.setActorId(event.actorId());
        entity.setAction(event.action());
        entity.setResourceType(event.resourceType());
        entity.setResourceId(event.resourceId());
        entity.setResult(event.result() != null ? event.result() : DEFAULT_RESULT);
        entity.setReasonCode(event.reasonCode());
        entity.setSourceIp(event.sourceIp());
        entity.setUserAgent(event.userAgent());
        entity.setTraceId(event.traceId());
        entity.setRequestId(event.requestId());
        entity.setPayloadBefore(event.payloadBefore());
        entity.setPayloadAfter(event.payloadAfter());
        return entity;
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return instant.atOffset(ZoneOffset.UTC);
    }
}
