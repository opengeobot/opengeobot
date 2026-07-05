/*
 * Function: MyBatis-Plus mapper for sys_permission — permission catalog queries
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.iam.domain.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Data access for {@link Permission}. Supports listing all permissions, filtering
 * by module, and resolving permissions granted to a role via the
 * {@code sys_role_permission} join table.
 */
@Mapper
public interface PermissionRepository extends BaseMapper<Permission> {

    @Select("SELECT * FROM platform_iam.sys_permission ORDER BY module, permission_code")
    List<Permission> findAll();

    @Select("SELECT * FROM platform_iam.sys_permission WHERE module = #{module} ORDER BY permission_code")
    List<Permission> findByModule(@Param("module") String module);

    @Select("SELECT p.* FROM platform_iam.sys_permission p " +
            "JOIN platform_iam.sys_role_permission rp ON p.permission_code = rp.permission_code " +
            "WHERE rp.role_id = #{roleId} ORDER BY p.permission_code")
    List<Permission> findByRoleId(@Param("roleId") String roleId);

    @Select("SELECT permission_code FROM platform_iam.sys_role_permission WHERE role_id = #{roleId}")
    List<String> findPermissionCodesByRoleId(@Param("roleId") String roleId);
}
