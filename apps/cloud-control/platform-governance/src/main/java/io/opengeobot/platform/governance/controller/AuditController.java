/*
 * Function: Audit REST controller — endpoint for querying operation audit logs
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.governance.dto.AuditLogDto;
import io.opengeobot.platform.governance.dto.AuditQueryRequest;
import io.opengeobot.platform.governance.service.AuditServiceImpl;
import io.opengeobot.platform.governance.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * REST controller for operation audit log queries. Exposes endpoints under
 * {@code /api/v1/audits} per the OpenAPI contract.
 * Permission: {@code audit.audit.read}.
 */
@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

    private final AuditServiceImpl auditService;

    public AuditController(AuditServiceImpl auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AuditLogDto> listAudits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) OffsetDateTime occurredFrom,
            @RequestParam(required = false) OffsetDateTime occurredTo) {
        AuditQueryRequest query = new AuditQueryRequest(
                actorId, action, resourceType, resourceId, traceId, occurredFrom, occurredTo);
        PageResult<AuditLogDto> result = auditService.query(query, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }
}
