package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.MemeVectorReconciliationJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 梗向量校准任务表的持久化操作。 */
@Mapper
public interface MemeVectorReconciliationJobMapper extends BaseMapper<MemeVectorReconciliationJob> {

    /** 通过唯一活动槽原子创建任务；并发启动时后到请求无副作用。 */
    @Insert("INSERT IGNORE INTO meme_vector_reconciliation_job "
            + "(active_slot, status, phase, published_cursor, published_scanned, upsert_queued, "
            + "vector_scanned, removal_queued, created_by, created_at, updated_at) "
            + "VALUES (1, 'PENDING', 'SYNC_PUBLISHED', 0, 0, 0, 0, 0, #{adminId}, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))")
    int insertActiveJob(@Param("adminId") long adminId);

    /** 查询当前活动任务并锁行，发起和恢复操作依靠 active_slot 唯一索引兜底并发。 */
    @Select("SELECT * FROM meme_vector_reconciliation_job "
            + "WHERE active_slot = 1 AND status IN ('PENDING', 'RUNNING') ORDER BY id ASC LIMIT 1 FOR UPDATE")
    MemeVectorReconciliationJob selectActiveJobForUpdate();

    /** 锁定指定任务，供管理员恢复操作校验状态。 */
    @Select("SELECT * FROM meme_vector_reconciliation_job WHERE id = #{jobId} LIMIT 1 FOR UPDATE")
    MemeVectorReconciliationJob selectByIdForUpdate(@Param("jobId") long jobId);

    /** 锁定一条没有有效租约的任务，SKIP LOCKED 使多个后端实例可以安全竞争。 */
    @Select("SELECT * FROM meme_vector_reconciliation_job "
            + "WHERE active_slot = 1 AND status IN ('PENDING', 'RUNNING') "
            + "AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(3)) "
            + "ORDER BY id ASC LIMIT 1 FOR UPDATE SKIP LOCKED")
    MemeVectorReconciliationJob selectNextRunnableForUpdate();

    /** 将已锁定任务租给当前 worker，并记录首次开始时间。 */
    @Update("UPDATE meme_vector_reconciliation_job SET status = 'RUNNING', lease_owner = #{owner}, "
            + "lease_until = #{leaseUntil}, started_at = COALESCE(started_at, CURRENT_TIMESTAMP(3)), "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{jobId} AND active_slot = 1 "
            + "AND status IN ('PENDING', 'RUNNING') AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(3))")
    int lease(@Param("jobId") long jobId,
              @Param("owner") String owner,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    /** 同事务推进 Java 游标和已写入 outbox 的 upsert 数量。 */
    @Update("UPDATE meme_vector_reconciliation_job SET published_cursor = #{cursor}, "
            + "published_scanned = published_scanned + #{count}, upsert_queued = upsert_queued + #{count}, "
            + "lease_until = #{leaseUntil}, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{jobId} AND status = 'RUNNING' AND lease_owner = #{owner}")
    int advancePublishedPage(@Param("jobId") long jobId,
                             @Param("owner") String owner,
                             @Param("cursor") long cursor,
                             @Param("count") int count,
                             @Param("leaseUntil") LocalDateTime leaseUntil);

    /** Java 扫描结束后切换到向量库孤儿清理阶段。 */
    @Update("UPDATE meme_vector_reconciliation_job SET phase = 'CLEAN_ORPHAN', "
            + "lease_until = #{leaseUntil}, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{jobId} AND status = 'RUNNING' AND lease_owner = #{owner}")
    int moveToOrphanCleanup(@Param("jobId") long jobId,
                            @Param("owner") String owner,
                            @Param("leaseUntil") LocalDateTime leaseUntil);

    /** 同事务推进向量游标及扫描数量、删除事件排队数量。 */
    @Update("UPDATE meme_vector_reconciliation_job SET vector_cursor = #{cursor}, "
            + "vector_scanned = vector_scanned + #{scanned}, removal_queued = removal_queued + #{removals}, "
            + "lease_until = #{leaseUntil}, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{jobId} AND status = 'RUNNING' AND lease_owner = #{owner}")
    int advanceVectorPage(@Param("jobId") long jobId,
                          @Param("owner") String owner,
                          @Param("cursor") String cursor,
                          @Param("scanned") int scanned,
                          @Param("removals") int removals,
                          @Param("leaseUntil") LocalDateTime leaseUntil);

    /** 最后一页事件写入 outbox 后标记扫描完成；事件投递仍由现有 outbox 负责。 */
    @Update("UPDATE meme_vector_reconciliation_job SET status = 'COMPLETED', active_slot = NULL, "
            + "vector_scanned = vector_scanned + #{scanned}, removal_queued = removal_queued + #{removals}, "
            + "lease_owner = NULL, lease_until = NULL, failure_stage = NULL, finished_at = CURRENT_TIMESTAMP(3), "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{jobId} AND status = 'RUNNING' AND lease_owner = #{owner}")
    int completeVectorPage(@Param("jobId") long jobId,
                           @Param("owner") String owner,
                           @Param("scanned") int scanned,
                           @Param("removals") int removals);

    /** 失败时释放活动槽和租约但保留两侧游标，便于管理员继续当前页。 */
    @Update("UPDATE meme_vector_reconciliation_job SET status = 'FAILED', active_slot = NULL, "
            + "lease_owner = NULL, lease_until = NULL, failure_stage = #{failureStage}, "
            + "finished_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{jobId} AND status = 'RUNNING' AND lease_owner = #{owner}")
    int markFailed(@Param("jobId") long jobId,
                   @Param("owner") String owner,
                   @Param("failureStage") String failureStage);

    /** 管理员继续失败任务时重新占用唯一活动槽并保留最后确认的游标。 */
    @Update("UPDATE IGNORE meme_vector_reconciliation_job SET status = 'PENDING', active_slot = 1, "
            + "lease_owner = NULL, lease_until = NULL, failure_stage = NULL, finished_at = NULL, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{jobId} AND status = 'FAILED'")
    int resumeFailed(@Param("jobId") long jobId);
}
