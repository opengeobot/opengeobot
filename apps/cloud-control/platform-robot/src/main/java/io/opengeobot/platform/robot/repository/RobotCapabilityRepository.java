/*
 * Function: Robot capability MyBatis-Plus mapper — CRUD for robot_registry.robot_capability
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.RobotCapability;

import java.util.List;

/**
 * MyBatis-Plus mapper for {@link RobotCapability}, providing standard CRUD
 * operations against the {@code robot_registry.robot_capability} table.
 */
public interface RobotCapabilityRepository extends BaseMapper<RobotCapability> {

    default List<RobotCapability> findByRobotId(String robotId) {
        LambdaQueryWrapper<RobotCapability> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotCapability::getRobotId, robotId);
        return selectList(wrapper);
    }

    default int deleteByRobotId(String robotId) {
        LambdaQueryWrapper<RobotCapability> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotCapability::getRobotId, robotId);
        return delete(wrapper);
    }
}
