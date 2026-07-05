/*
 * Function: MyBatis-Plus mapper for sys_operation_audit table
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.common.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.common.audit.AuditEventEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for the {@code platform_governance.sys_operation_audit}
 * table. Supports standard CRUD via {@link BaseMapper}. Audit records are
 * append-only; inserts only.
 */
@Mapper
public interface AuditEventMapper extends BaseMapper<AuditEventEntity> {
}
