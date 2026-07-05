/*
 * Function: MyBatis-Plus mapper for outbox_event table
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.common.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.opengeobot.platform.common.event.OutboxEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MyBatis-Plus mapper for the {@code platform_governance.outbox_event} table.
 * Supports standard CRUD via {@link BaseMapper} plus polling for unpublished
 * events and marking events as published.
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {

    @Select("SELECT * FROM platform_governance.outbox_event WHERE published = FALSE ORDER BY occurred_at LIMIT #{limit}")
    List<OutboxEventEntity> findUnpublished(@Param("limit") int limit);

    @Update("UPDATE platform_governance.outbox_event SET published = TRUE, published_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int markPublished(@Param("id") Long id);
}
