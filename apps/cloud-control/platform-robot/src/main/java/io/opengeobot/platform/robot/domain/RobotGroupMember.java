/*
 * Function: Robot group member entity — maps to robot_registry.robot_group_member table
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Persistent robot group member entity backed by the
 * {@code robot_registry.robot_group_member} table. Each row associates a
 * robot with a group, recording when the robot joined.
 */
@TableName(value = "robot_group_member", schema = "robot_registry")
public class RobotGroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String robotId;

    private String groupId;

    private OffsetDateTime joinedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
