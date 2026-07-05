/*
 * Function: Robot MyBatis-Plus mapper — CRUD for robot_registry.robot
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.Robot;

import java.util.List;

/**
 * MyBatis-Plus mapper for {@link Robot}, providing standard CRUD operations
 * against the {@code robot_registry.robot} table. Additional finders cover
 * lookups by public robot id, operational status and owning organisation.
 */
public interface RobotRepository extends BaseMapper<Robot> {

    default Robot findByRobotId(String robotId) {
        LambdaQueryWrapper<Robot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Robot::getRobotId, robotId);
        return selectOne(wrapper);
    }

    default List<Robot> findByStatus(String status) {
        LambdaQueryWrapper<Robot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Robot::getStatus, status);
        return selectList(wrapper);
    }

    default List<Robot> findByOrgId(String orgId) {
        LambdaQueryWrapper<Robot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Robot::getOrgId, orgId);
        return selectList(wrapper);
    }
}
