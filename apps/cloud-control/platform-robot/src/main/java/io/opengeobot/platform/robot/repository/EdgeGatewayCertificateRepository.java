/*
 * Function: Edge gateway certificate MyBatis-Plus mapper
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.EdgeGatewayCertificate;

import java.util.List;

/**
 * MyBatis-Plus mapper for {@link EdgeGatewayCertificate}.
 */
public interface EdgeGatewayCertificateRepository extends BaseMapper<EdgeGatewayCertificate> {

    default EdgeGatewayCertificate findActiveByGatewayId(String gatewayId) {
        LambdaQueryWrapper<EdgeGatewayCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EdgeGatewayCertificate::getGatewayId, gatewayId)
                .eq(EdgeGatewayCertificate::getStatus, "ACTIVE")
                .last("LIMIT 1");
        return selectOne(wrapper);
    }

    default List<EdgeGatewayCertificate> findByGatewayId(String gatewayId) {
        LambdaQueryWrapper<EdgeGatewayCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EdgeGatewayCertificate::getGatewayId, gatewayId)
                .orderByDesc(EdgeGatewayCertificate::getCreatedAt);
        return selectList(wrapper);
    }
}
