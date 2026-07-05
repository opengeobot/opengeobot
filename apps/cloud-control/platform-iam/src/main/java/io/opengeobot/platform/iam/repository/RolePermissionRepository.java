/*
 * Function: MyBatis-Plus mapper for sys_role_permission — role-permission association queries
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.iam.domain.RolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Data access for {@link RolePermission}. Supports lookup by role and
 * bulk deletion of all permission grants for a role (used during permission replacement).
 */
@Mapper
public interface RolePermissionRepository extends BaseMapper<RolePermission> {

    @Select("SELECT * FROM platform_iam.sys_role_permission WHERE role_id = #{roleId}")
    List<RolePermission> findByRoleId(@Param("roleId") String roleId);

    @Delete("DELETE FROM platform_iam.sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") String roleId);
}
