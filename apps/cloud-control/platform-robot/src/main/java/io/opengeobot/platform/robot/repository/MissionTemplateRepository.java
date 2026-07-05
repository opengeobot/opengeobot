/*
 * Function: Mission template repository — MyBatis-Plus mapper for mission.mission_template
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.MissionTemplate;

import java.util.List;

/**
 * MyBatis-Plus mapper for the {@code mission.mission_template} table. Templates
 * are returned ordered by most recently updated.
 */
public interface MissionTemplateRepository extends BaseMapper<MissionTemplate> {

    default List<MissionTemplate> selectPage(long offset, int limit) {
        QueryWrapper<MissionTemplate> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("updated_at");
        wrapper.last("LIMIT " + limit + " OFFSET " + offset);
        return selectList(wrapper);
    }

    default long countAll() {
        return selectCount(new QueryWrapper<>());
    }
}
