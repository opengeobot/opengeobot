/*
 * Function: ConfigHistory MyBatis-Plus mapper — append-only writes for sys_config_history
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.config.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.governance.domain.config.ConfigHistory;

/**
 * MyBatis-Plus mapper for {@link ConfigHistory}, providing insert and query
 * operations against the append-only {@code platform_governance.sys_config_history}
 * table.
 */
public interface ConfigHistoryRepository extends BaseMapper<ConfigHistory> {
}
