/*
 * Function: Robot group MyBatis-Plus mapper — CRUD for robot_registry.robot_group
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.RobotGroup;

/**
 * MyBatis-Plus mapper for {@link RobotGroup}, providing standard CRUD
 * operations against the {@code robot_registry.robot_group} table.
 */
public interface RobotGroupRepository extends BaseMapper<RobotGroup> {

    default RobotGroup findByGroupId(String groupId) {
        LambdaQueryWrapper<RobotGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotGroup::getGroupId, groupId);
        return selectOne(wrapper);
    }

    default java.util.List<RobotGroup> findByParentId(String parentId) {
        LambdaQueryWrapper<RobotGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotGroup::getParentId, parentId);
        return selectList(wrapper);
    }
}
