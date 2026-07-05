/*
 * Function: MyBatis-Plus mapper for sys_session
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.iam.domain.Session;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Data access for {@link Session}. Supports lookup by session_id and
 * retrieval of all active (non-revoked, non-expired) sessions for a user.
 */
@Mapper
public interface SessionRepository extends BaseMapper<Session> {

    @Select("SELECT * FROM platform_iam.sys_session WHERE session_id = #{sessionId}")
    Session findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM platform_iam.sys_session " +
            "WHERE user_id = #{userId} AND state = 'ACTIVE' AND expires_at > NOW() " +
            "ORDER BY issued_at DESC")
    List<Session> findActiveByUserId(@Param("userId") String userId);
}
