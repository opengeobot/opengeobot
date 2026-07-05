/*
 * Function: ConflictRecord repository — MyBatis-Plus mapper for fleet.conflict_record
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.ConflictRecord;

import java.util.List;

/**
 * MyBatis-Plus mapper for {@link ConflictRecord}, providing standard CRUD
 * operations against the {@code fleet.conflict_record} table. Additional
 * finders cover lookups by public conflict id and filtering by status.
 */
public interface ConflictRecordRepository extends BaseMapper<ConflictRecord> {

    default ConflictRecord findByConflictId(String conflictId) {
        QueryWrapper<ConflictRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("conflict_id", conflictId);
        return selectOne(wrapper);
    }

    default List<ConflictRecord> selectByFilter(String status, long offset, int limit) {
        QueryWrapper<ConflictRecord> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("detected_at");
        wrapper.last("LIMIT " + limit + " OFFSET " + offset);
        return selectList(wrapper);
    }

    default long countByFilter(String status) {
        QueryWrapper<ConflictRecord> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        return selectCount(wrapper);
    }
}
