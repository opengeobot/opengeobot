/*
 * Function: Adapter compatibility MyBatis-Plus mapper - CRUD for robot_registry.adapter_compatibility
 * Time: 2026-07-09
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.adapter.AdapterCompatibility;

import java.util.List;

/**
 * MyBatis-Plus mapper for {@link AdapterCompatibility}, providing standard CRUD
 * operations against the {@code robot_registry.adapter_compatibility} table.
 */
public interface AdapterCompatibilityRepository extends BaseMapper<AdapterCompatibility> {

    default AdapterCompatibility findByAdapterId(String adapterId) {
        LambdaQueryWrapper<AdapterCompatibility> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdapterCompatibility::getAdapterId, adapterId);
        return selectOne(wrapper);
    }

    default List<AdapterCompatibility> findAllByRobotModelId(String robotModelId) {
        LambdaQueryWrapper<AdapterCompatibility> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdapterCompatibility::getRobotModelId, robotModelId)
                .orderByAsc(AdapterCompatibility::getAdapterType);
        return selectList(wrapper);
    }
}
