package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetBackfillState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 中文注释：历史文件引用回填游标和租约的持久化映射。 */
@Mapper
public interface FileAssetBackfillStateMapper {

    /** 中文注释：为所有回填数据源和全局租约行补齐初始检查点，不覆盖已有进度。 */
    @Insert({"<script>",
            "INSERT IGNORE INTO file_asset_backfill_state (backfill_key, source_name, status) VALUES",
            "<foreach collection='sourceNames' item='sourceName' separator=','>",
            "(#{backfillKey}, #{sourceName}, 'PENDING')",
            "</foreach>",
            "</script>"})
    int ensureStateRows(@Param("backfillKey") String backfillKey,
                        @Param("sourceNames") List<String> sourceNames);

    /** 中文注释：使用条件更新原子领取唯一全局租约，避免多实例同时扫描历史数据。 */
    @Update("UPDATE file_asset_backfill_state SET status = 'RUNNING', lease_owner = #{leaseOwner}, "
            + "lease_until = #{leaseUntil}, started_at = COALESCE(started_at, CURRENT_TIMESTAMP), "
            + "last_error = NULL, updated_at = CURRENT_TIMESTAMP "
            + "WHERE backfill_key = #{backfillKey} AND source_name = '__runner__' "
            + "AND status <> 'COMPLETED' AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP "
            + "OR lease_owner = #{leaseOwner})")
    int claimRunnerLease(@Param("backfillKey") String backfillKey,
                         @Param("leaseOwner") String leaseOwner,
                         @Param("leaseUntil") LocalDateTime leaseUntil);

    /** 中文注释：按优先顺序锁定第一个未完成数据源，断点续跑时继续之前的游标。 */
    @Select("SELECT * FROM file_asset_backfill_state WHERE backfill_key = #{backfillKey} "
            + "AND source_name <> '__runner__' AND status <> 'COMPLETED' "
            + "ORDER BY FIELD(source_name, 'USER_AVATAR', 'MEME_COVER', 'MEME_RESOURCE', "
            + "'COMMENT_IMAGE', 'BANNER_IMAGE', 'MEME_REVISION'), source_name LIMIT 1 FOR UPDATE")
    FileAssetBackfillState selectNextIncompleteSource(@Param("backfillKey") String backfillKey);

    /** 中文注释：一个源表批次成功后原子保存游标和完成状态。 */
    @Update("UPDATE file_asset_backfill_state SET last_source_id = COALESCE(#{lastSourceId}, last_source_id), "
            + "status = #{status}, completed_at = IF(#{status} = 'COMPLETED', CURRENT_TIMESTAMP, NULL), "
            + "last_error = NULL, updated_at = CURRENT_TIMESTAMP "
            + "WHERE backfill_key = #{backfillKey} AND source_name = #{sourceName}")
    int markSourceProgress(@Param("backfillKey") String backfillKey,
                           @Param("sourceName") String sourceName,
                           @Param("lastSourceId") Long lastSourceId,
                           @Param("status") String status);

    /** 中文注释：统计仍未完成的数据源；所有源完成后才允许打开删除门禁。 */
    @Select("SELECT COUNT(*) FROM file_asset_backfill_state WHERE backfill_key = #{backfillKey} "
            + "AND source_name <> '__runner__' AND status <> 'COMPLETED'")
    long countIncompleteSourceStates(@Param("backfillKey") String backfillKey);

    /** 中文注释：记录全局回填完成或释放租约，供其他实例继续处理下一批。 */
    @Update("UPDATE file_asset_backfill_state SET status = #{status}, lease_owner = NULL, lease_until = NULL, "
            + "completed_at = IF(#{status} = 'COMPLETED', CURRENT_TIMESTAMP, NULL), "
            + "last_error = #{lastError}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE backfill_key = #{backfillKey} AND source_name = '__runner__' "
            + "AND lease_owner = #{leaseOwner}")
    int releaseRunnerLease(@Param("backfillKey") String backfillKey,
                           @Param("leaseOwner") String leaseOwner,
                           @Param("status") String status,
                           @Param("lastError") String lastError);

    /** 中文注释：统计回填状态行，空表时删除 Worker 必须保持关闭。 */
    @Select("SELECT COUNT(*) FROM file_asset_backfill_state WHERE backfill_key = #{backfillKey}")
    long countAllStates(@Param("backfillKey") String backfillKey);

    /** 中文注释：统计未完成状态行，包含全局租约行。 */
    @Select("SELECT COUNT(*) FROM file_asset_backfill_state WHERE backfill_key = #{backfillKey} "
            + "AND status <> 'COMPLETED'")
    long countIncompleteStates(@Param("backfillKey") String backfillKey);
}
