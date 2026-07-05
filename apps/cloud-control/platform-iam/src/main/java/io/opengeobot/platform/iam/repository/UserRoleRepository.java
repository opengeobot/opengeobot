/*
 * Function: MyBatis-Plus mapper for sys_user_role — user-role association queries
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.iam.domain.UserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Data access for {@link UserRole}. Supports lookup by user or role and
 * bulk deletion of all role assignments for a user (used during role replacement).
 */
@Mapper
public interface UserRoleRepository extends BaseMapper<UserRole> {

    @Select("SELECT * FROM platform_iam.sys_user_role WHERE user_id = #{userId}")
    List<UserRole> findByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM platform_iam.sys_user_role WHERE role_id = #{roleId}")
    List<UserRole> findByRoleId(@Param("roleId") String roleId);

    @Delete("DELETE FROM platform_iam.sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") String userId);
}
