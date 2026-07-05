/*
 * Function: Robot status history MyBatis-Plus mapper — CRUD for robot_registry.robot_status_history
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.RobotStatusHistory;

/**
 * MyBatis-Plus mapper for {@link RobotStatusHistory}, providing standard CRUD
 * operations against the {@code robot_registry.robot_status_history} table.
 * Rows are append-only audit records written during SM-ROBOT-001 transitions.
 */
public interface RobotStatusHistoryRepository extends BaseMapper<RobotStatusHistory> {
}
