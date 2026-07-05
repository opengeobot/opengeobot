/*
 * Function: MyBatis-Plus mapper for sys_role — role lookup and user-role join
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.iam.domain.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Data access for {@link Role}. Supports lookup by role code and by user id
 * via the {@code sys_user_role} join table.
 */
@Mapper
public interface RoleRepository extends BaseMapper<Role> {

    @Select("SELECT * FROM platform_iam.sys_role WHERE role_code = #{roleCode}")
    Role findByRoleCode(@Param("roleCode") String roleCode);

    @Select("SELECT r.* FROM platform_iam.sys_role r " +
            "JOIN platform_iam.sys_user_role ur ON r.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} ORDER BY r.sort_order, r.role_name")
    List<Role> findByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM platform_iam.sys_role WHERE role_id = #{roleId}")
    Role findByRoleId(@Param("roleId") String roleId);
}
