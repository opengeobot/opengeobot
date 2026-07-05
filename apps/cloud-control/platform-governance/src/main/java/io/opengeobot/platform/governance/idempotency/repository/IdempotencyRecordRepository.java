/*
 * Function: IdempotencyRecord MyBatis-Plus mapper — CRUD for sys_idempotency_record
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.idempotency.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.governance.domain.idempotency.IdempotencyRecord;

/**
 * MyBatis-Plus mapper for {@link IdempotencyRecord}, providing standard CRUD
 * operations against the {@code platform_governance.sys_idempotency_record}
 * table.
 */
public interface IdempotencyRecordRepository extends BaseMapper<IdempotencyRecord> {
}
