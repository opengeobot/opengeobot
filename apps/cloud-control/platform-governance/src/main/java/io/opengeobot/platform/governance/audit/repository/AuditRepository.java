/*
 * Function: Audit MyBatis-Plus mapper — insert and query for sys_operation_audit
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.audit.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.governance.domain.audit.OperationAudit;

/**
 * MyBatis-Plus mapper for {@link OperationAudit}, providing insert and query
 * operations against the append-only
 * {@code platform_governance.sys_operation_audit} table.
 */
public interface AuditRepository extends BaseMapper<OperationAudit> {
}
