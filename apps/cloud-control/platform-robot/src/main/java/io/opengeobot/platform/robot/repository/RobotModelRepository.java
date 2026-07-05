/*
 * Function: Robot model MyBatis-Plus mapper — CRUD for robot_registry.robot_model
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.RobotModel;

/**
 * MyBatis-Plus mapper for {@link RobotModel}, providing standard CRUD
 * operations against the {@code robot_registry.robot_model} table.
 */
public interface RobotModelRepository extends BaseMapper<RobotModel> {

    default RobotModel findByModelId(String modelId) {
        LambdaQueryWrapper<RobotModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotModel::getModelId, modelId);
        return selectOne(wrapper);
    }
}
