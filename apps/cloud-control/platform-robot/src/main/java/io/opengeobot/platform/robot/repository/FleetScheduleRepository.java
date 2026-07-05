/*
 * Function: FleetSchedule repository — MyBatis-Plus mapper for fleet.fleet_schedule
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.FleetSchedule;

import java.util.List;

/**
 * MyBatis-Plus mapper for {@link FleetSchedule}, providing standard CRUD
 * operations against the {@code fleet.fleet_schedule} table. Additional
 * finders cover lookups by public schedule id, robot, mission and filtering
 * by status.
 */
public interface FleetScheduleRepository extends BaseMapper<FleetSchedule> {

    default FleetSchedule findByScheduleId(String scheduleId) {
        QueryWrapper<FleetSchedule> wrapper = new QueryWrapper<>();
        wrapper.eq("schedule_id", scheduleId);
        return selectOne(wrapper);
    }

    default List<FleetSchedule> selectByFilter(String status, String robotId, String missionId, long offset, int limit) {
        QueryWrapper<FleetSchedule> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (robotId != null && !robotId.isBlank()) {
            wrapper.eq("robot_id", robotId);
        }
        if (missionId != null && !missionId.isBlank()) {
            wrapper.eq("mission_id", missionId);
        }
        wrapper.orderByDesc("created_at");
        wrapper.last("LIMIT " + limit + " OFFSET " + offset);
        return selectList(wrapper);
    }

    default long countByFilter(String status, String robotId, String missionId) {
        QueryWrapper<FleetSchedule> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (robotId != null && !robotId.isBlank()) {
            wrapper.eq("robot_id", robotId);
        }
        if (missionId != null && !missionId.isBlank()) {
            wrapper.eq("mission_id", missionId);
        }
        return selectCount(wrapper);
    }

    /**
     * Returns schedules for a robot that overlap with the given time window.
     * Used by the conflict detector to find time-overlap conflicts.
     */
    default List<FleetSchedule> selectOverlapping(String robotId, java.time.OffsetDateTime plannedStart,
                                                   java.time.OffsetDateTime plannedEnd) {
        QueryWrapper<FleetSchedule> wrapper = new QueryWrapper<>();
        wrapper.eq("robot_id", robotId)
                .in("status", "PENDING", "APPROVED", "ACTIVE")
                .lt("planned_start", plannedEnd)
                .gt("planned_end", plannedStart);
        return selectList(wrapper);
    }

    /**
     * Returns schedules for a robot that are in an active or pending state.
     */
    default List<FleetSchedule> selectActiveByRobotId(String robotId) {
        QueryWrapper<FleetSchedule> wrapper = new QueryWrapper<>();
        wrapper.eq("robot_id", robotId)
                .in("status", "PENDING", "APPROVED", "ACTIVE")
                .orderByDesc("updated_at");
        return selectList(wrapper);
    }
}
