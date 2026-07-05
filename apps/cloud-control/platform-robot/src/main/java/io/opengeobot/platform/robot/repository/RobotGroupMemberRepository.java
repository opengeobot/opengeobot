/*
 * Function: Robot group member MyBatis-Plus mapper — CRUD for robot_registry.robot_group_member
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.RobotGroupMember;

import java.util.List;

/**
 * MyBatis-Plus mapper for {@link RobotGroupMember}, providing standard CRUD
 * operations against the {@code robot_registry.robot_group_member} table.
 */
public interface RobotGroupMemberRepository extends BaseMapper<RobotGroupMember> {

    default List<RobotGroupMember> findByGroupId(String groupId) {
        LambdaQueryWrapper<RobotGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotGroupMember::getGroupId, groupId);
        return selectList(wrapper);
    }

    default List<RobotGroupMember> findByRobotId(String robotId) {
        LambdaQueryWrapper<RobotGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotGroupMember::getRobotId, robotId);
        return selectList(wrapper);
    }

    default int deleteByGroupId(String groupId) {
        LambdaQueryWrapper<RobotGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotGroupMember::getGroupId, groupId);
        return delete(wrapper);
    }

    default RobotGroupMember findByRobotIdAndGroupId(String robotId, String groupId) {
        LambdaQueryWrapper<RobotGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotGroupMember::getRobotId, robotId)
                .eq(RobotGroupMember::getGroupId, groupId);
        return selectOne(wrapper);
    }
}
