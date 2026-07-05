/*
 * Function: Mission approval repository — MyBatis-Plus mapper for mission.mission_approval
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.MissionApproval;

/**
 * MyBatis-Plus mapper for the {@code mission.mission_approval} table. Looks up
 * the most recent pending approval for a mission to support the
 * SM-APPROVAL-001 state machine.
 */
public interface MissionApprovalRepository extends BaseMapper<MissionApproval> {

    default MissionApproval selectLatestByMissionId(String missionId) {
        QueryWrapper<MissionApproval> wrapper = new QueryWrapper<>();
        wrapper.eq("mission_id", missionId);
        wrapper.orderByDesc("created_at");
        wrapper.last("LIMIT 1");
        return selectOne(wrapper);
    }

    default MissionApproval selectPendingByMissionId(String missionId) {
        QueryWrapper<MissionApproval> wrapper = new QueryWrapper<>();
        wrapper.eq("mission_id", missionId);
        wrapper.eq("status", "PENDING");
        wrapper.orderByDesc("created_at");
        wrapper.last("LIMIT 1");
        return selectOne(wrapper);
    }
}
