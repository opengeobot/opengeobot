/*
 * Function: Edge gateway MyBatis-Plus mapper — CRUD for robot_registry.edge_gateway
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.EdgeGateway;

/**
 * MyBatis-Plus mapper for {@link EdgeGateway}.
 */
public interface EdgeGatewayRepository extends BaseMapper<EdgeGateway> {

    default EdgeGateway findByGatewayId(String gatewayId) {
        LambdaQueryWrapper<EdgeGateway> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EdgeGateway::getGatewayId, gatewayId);
        return selectOne(wrapper);
    }
}
