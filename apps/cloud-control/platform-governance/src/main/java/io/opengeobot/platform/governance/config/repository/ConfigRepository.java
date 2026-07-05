/*
 * Function: SysConfig MyBatis-Plus mapper — CRUD for sys_config
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.config.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.governance.domain.config.SysConfig;

/**
 * MyBatis-Plus mapper for {@link SysConfig}, providing standard CRUD
 * operations against the {@code platform_governance.sys_config} table.
 */
public interface ConfigRepository extends BaseMapper<SysConfig> {
}
