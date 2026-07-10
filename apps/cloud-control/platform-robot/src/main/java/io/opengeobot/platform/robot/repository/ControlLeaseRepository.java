/*
 * Function: Control lease MyBatis-Plus mapper — CRUD for fleet.control_lease
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.ControlLease;

/**
 * MyBatis-Plus mapper for {@link ControlLease}.
 */
public interface ControlLeaseRepository extends BaseMapper<ControlLease> {

    default ControlLease findByLeaseId(String leaseId) {
        LambdaQueryWrapper<ControlLease> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ControlLease::getLeaseId, leaseId);
        return selectOne(wrapper);
    }

    default ControlLease findActiveByRobotId(String robotId) {
        LambdaQueryWrapper<ControlLease> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ControlLease::getRobotId, robotId)
                .eq(ControlLease::getStatus, "ACTIVE")
                .orderByDesc(ControlLease::getAcquiredAt)
                .last("LIMIT 1");
        return selectOne(wrapper);
    }
}
