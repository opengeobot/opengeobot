/*
 * Function: Audit service interface — record audit events to PostgreSQL
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.audit;

/**
 * Service for recording audit events. Implementations persist to the
 * {@code sys_operation_audit} table in PostgreSQL.
 */
public interface AuditService {

    void record(AuditEvent event);
}
