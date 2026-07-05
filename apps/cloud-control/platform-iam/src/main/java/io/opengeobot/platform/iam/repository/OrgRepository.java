/*
 * Function: MyBatis-Plus mapper for sys_org — organization tree queries
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.iam.domain.Org;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Data access for {@link Org}. Supports lookup by parent, code and direct
 * children retrieval for building the organization tree.
 */
@Mapper
public interface OrgRepository extends BaseMapper<Org> {

    @Select("SELECT * FROM platform_iam.sys_org WHERE parent_id = #{parentId} ORDER BY sort_order, org_name")
    List<Org> findByParentId(@Param("parentId") String parentId);

    @Select("SELECT * FROM platform_iam.sys_org WHERE org_code = #{orgCode}")
    Org findByOrgCode(@Param("orgCode") String orgCode);

    @Select("SELECT * FROM platform_iam.sys_org WHERE parent_id IS NULL ORDER BY sort_order, org_name")
    List<Org> findRoots();

    @Select("SELECT * FROM platform_iam.sys_org WHERE org_id = #{orgId}")
    Org findByOrgId(@Param("orgId") String orgId);

    @Select("SELECT * FROM platform_iam.sys_org WHERE path LIKE CONCAT(#{pathPrefix}, '%') ORDER BY sort_order, org_name")
    List<Org> findDescendantsByPath(@Param("pathPrefix") String pathPrefix);
}
