/*
 * Function: MyBatis-Plus mapper for sys_user_org — user-organization association queries
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.iam.domain.UserOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Data access for {@link UserOrg}. Supports lookup by user or organization.
 */
@Mapper
public interface UserOrgRepository extends BaseMapper<UserOrg> {

    @Select("SELECT * FROM platform_iam.sys_user_org WHERE user_id = #{userId}")
    List<UserOrg> findByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM platform_iam.sys_user_org WHERE org_id = #{orgId}")
    List<UserOrg> findByOrgId(@Param("orgId") String orgId);

    @Select("SELECT * FROM platform_iam.sys_user_org WHERE user_id = #{userId} AND is_primary = TRUE")
    UserOrg findPrimaryByUserId(@Param("userId") String userId);
}
