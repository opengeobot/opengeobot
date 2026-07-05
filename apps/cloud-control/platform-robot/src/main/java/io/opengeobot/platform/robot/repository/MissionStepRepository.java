/*
 * Function: Mission step repository — MyBatis-Plus mapper for mission.mission_step
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.MissionStep;

import java.util.List;

/**
 * MyBatis-Plus mapper for the {@code mission.mission_step} table. Steps are
 * always loaded ordered by {@code step_order} for a given mission.
 */
public interface MissionStepRepository extends BaseMapper<MissionStep> {

    default List<MissionStep> selectByMissionId(String missionId) {
        QueryWrapper<MissionStep> wrapper = new QueryWrapper<>();
        wrapper.eq("mission_id", missionId);
        wrapper.orderByAsc("step_order");
        return selectList(wrapper);
    }

    default int deleteByMissionId(String missionId) {
        QueryWrapper<MissionStep> wrapper = new QueryWrapper<>();
        wrapper.eq("mission_id", missionId);
        return delete(wrapper);
    }
}
