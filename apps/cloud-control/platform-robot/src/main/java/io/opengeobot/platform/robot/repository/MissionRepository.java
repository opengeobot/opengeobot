/*
 * Function: Mission repository — MyBatis-Plus mapper for mission.mission
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.Mission;

import java.util.List;

/**
 * MyBatis-Plus mapper for the {@code mission.mission} table. Provides
 * filtering by status and robot_id via {@link QueryWrapper} and is picked up by
 * the platform {@code @MapperScan} on {@code io.opengeobot.platform.**.repository}.
 */
public interface MissionRepository extends BaseMapper<Mission> {

    default List<Mission> selectByFilter(String status, String robotId, long offset, int limit) {
        QueryWrapper<Mission> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (robotId != null && !robotId.isBlank()) {
            wrapper.eq("robot_id", robotId);
        }
        wrapper.orderByDesc("updated_at");
        wrapper.last("LIMIT " + limit + " OFFSET " + offset);
        return selectList(wrapper);
    }

    default long countByFilter(String status, String robotId) {
        QueryWrapper<Mission> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (robotId != null && !robotId.isBlank()) {
            wrapper.eq("robot_id", robotId);
        }
        return selectCount(wrapper);
    }
}
