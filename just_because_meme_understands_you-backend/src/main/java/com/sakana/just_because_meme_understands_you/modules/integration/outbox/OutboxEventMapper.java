package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 事务发件箱的持久化入口。 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    /** 以行锁方式取得到期事件，允许多实例安全竞争。 */
    @Select("""
            SELECT *
            FROM integration_outbox_event
            WHERE ((status IN ('PENDING', 'RETRY') AND next_attempt_at <= #{now})
               OR (status = 'PROCESSING' AND locked_at <= #{expiredBefore}))
              AND NOT EXISTS (
                  SELECT 1
                  FROM integration_outbox_event earlier
                  WHERE earlier.aggregate_type = integration_outbox_event.aggregate_type
                    AND earlier.aggregate_id = integration_outbox_event.aggregate_id
                    AND earlier.id < integration_outbox_event.id
                    AND earlier.status NOT IN ('DELIVERED', 'DEAD')
              )
            ORDER BY id ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxEvent> selectClaimable(
            @Param("now") LocalDateTime now,
            @Param("expiredBefore") LocalDateTime expiredBefore,
            @Param("limit") int limit);

    /** 记录事件成功投递。 */
    @Update("UPDATE integration_outbox_event SET status = 'DELIVERED', delivered_at = #{now}, locked_at = NULL, last_error = NULL "
            + "WHERE id = #{id} AND status = 'PROCESSING' AND locked_at = #{lockedAt}")
    int markDelivered(@Param("id") long id,
                      @Param("lockedAt") LocalDateTime lockedAt,
                      @Param("now") LocalDateTime now);

    /** 记录失败事件的重试计划或最终失败状态。 */
    @Update("UPDATE integration_outbox_event SET status = #{status}, attempts = #{attempts}, next_attempt_at = #{nextAttemptAt}, locked_at = NULL, last_error = #{lastError} "
            + "WHERE id = #{id} AND status = 'PROCESSING' AND locked_at = #{lockedAt}")
    int markFailed(
            @Param("id") long id,
            @Param("lockedAt") LocalDateTime lockedAt,
            @Param("status") String status,
            @Param("attempts") int attempts,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError);
}
