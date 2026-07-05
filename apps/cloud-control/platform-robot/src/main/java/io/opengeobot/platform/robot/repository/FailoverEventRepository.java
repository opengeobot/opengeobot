/*
 * Function: FailoverEvent repository — MyBatis-Plus mapper for fleet.failover_event
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.FailoverEvent;

import java.util.List;

/**
 * MyBatis-Plus mapper for {@link FailoverEvent}, providing standard CRUD
 * operations against the {@code fleet.failover_event} table. Additional
 * finders cover lookups by public failover id, source robot and filtering
 * by status.
 */
public interface FailoverEventRepository extends BaseMapper<FailoverEvent> {

    default FailoverEvent findByFailoverId(String failoverId) {
        QueryWrapper<FailoverEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("failover_id", failoverId);
        return selectOne(wrapper);
    }

    default List<FailoverEvent> selectByFilter(String robotId, String status, long offset, int limit) {
        QueryWrapper<FailoverEvent> wrapper = new QueryWrapper<>();
        if (robotId != null && !robotId.isBlank()) {
            wrapper.eq("robot_id", robotId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("occurred_at");
        wrapper.last("LIMIT " + limit + " OFFSET " + offset);
        return selectList(wrapper);
    }

    default long countByFilter(String robotId, String status) {
        QueryWrapper<FailoverEvent> wrapper = new QueryWrapper<>();
        if (robotId != null && !robotId.isBlank()) {
            wrapper.eq("robot_id", robotId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        return selectCount(wrapper);
    }
}
