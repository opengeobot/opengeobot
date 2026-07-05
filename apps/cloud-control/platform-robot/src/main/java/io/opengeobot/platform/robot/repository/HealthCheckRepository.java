/*
 * Function: HealthCheck MyBatis-Plus mapper — CRUD for ops.health_check
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.robot.domain.HealthCheck;

/**
 * MyBatis-Plus mapper for {@link HealthCheck}, providing standard CRUD
 * operations against the {@code ops.health_check} table.
 */
public interface HealthCheckRepository extends BaseMapper<HealthCheck> {
}
