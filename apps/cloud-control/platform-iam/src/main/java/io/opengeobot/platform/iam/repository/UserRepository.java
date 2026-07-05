/*
 * Function: MyBatis-Plus mapper for sys_user and permission lookups
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.iam.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Data access for {@link User}. Supports lookup by username and public user_id,
 * and resolves the permission codes granted to the user through the RBAC tables
 * (sys_user_role → sys_role_permission).
 */
@Mapper
public interface UserRepository extends BaseMapper<User> {

    @Select("SELECT * FROM platform_iam.sys_user WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT * FROM platform_iam.sys_user WHERE user_id = #{userId}")
    User findByUserId(@Param("userId") String userId);

    @Select("SELECT rp.permission_code FROM platform_iam.sys_user_role ur " +
            "JOIN platform_iam.sys_role_permission rp ON ur.role_id = rp.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> findPermissionCodesByUserId(@Param("userId") String userId);
}
