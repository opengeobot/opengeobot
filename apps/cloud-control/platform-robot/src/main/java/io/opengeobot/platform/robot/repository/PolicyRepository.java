/*
 * Function: Policy repository — MyBatis-Plus mapper for policy.policy
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.Policy;

/**
 * MyBatis-Plus mapper for {@link Policy}, providing standard CRUD operations
 * against the {@code policy.policy} table.
 */
public interface PolicyRepository extends BaseMapper<Policy> {
}
