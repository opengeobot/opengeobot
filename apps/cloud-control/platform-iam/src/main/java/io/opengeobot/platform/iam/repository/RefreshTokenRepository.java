/*
 * Function: MyBatis-Plus mapper for sys_refresh_token
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.iam.domain.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Data access for {@link RefreshToken}. Supports lookup by token_id and
 * retrieval of all valid (non-revoked, non-expired) tokens for a user.
 */
@Mapper
public interface RefreshTokenRepository extends BaseMapper<RefreshToken> {

    @Select("SELECT * FROM platform_iam.sys_refresh_token WHERE token_id = #{tokenId}")
    RefreshToken findByTokenId(@Param("tokenId") String tokenId);

    @Select("SELECT * FROM platform_iam.sys_refresh_token " +
            "WHERE user_id = #{userId} AND revoked = FALSE AND expires_at > NOW() " +
            "ORDER BY created_at DESC")
    List<RefreshToken> findValidByUserId(@Param("userId") String userId);
}
