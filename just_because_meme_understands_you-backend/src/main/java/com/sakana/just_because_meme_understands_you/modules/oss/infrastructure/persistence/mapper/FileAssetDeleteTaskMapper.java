package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 中文注释：文件资产删除任务的持久化映射及并发领取查询。 */
@Mapper
public interface FileAssetDeleteTaskMapper extends BaseMapper<FileAssetDeleteTask> {

    /** 中文注释：返回指定状态的删除任务数，供每日对账汇总失败任务。 */
    @Select("SELECT COUNT(*) FROM file_delete_task WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /** 中文注释：按主键锁定删除任务，供状态转换在数据库事务中串行执行。 */
    @Select("SELECT * FROM file_delete_task WHERE id = #{taskId} FOR UPDATE")
    FileAssetDeleteTask selectByIdForUpdate(@Param("taskId") Long taskId);

    /** 中文注释：在事务中有界领取到期任务，跳过已被其他实例锁定的行。 */
    @Select("SELECT * FROM file_delete_task "
            + "WHERE (status = 'WAITING' AND next_attempt_at <= #{now}) "
            + "OR (status = 'PROCESSING' AND lease_until <= #{now}) "
            + "ORDER BY next_attempt_at, id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<FileAssetDeleteTask> selectDueForUpdate(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** 中文注释：资产重新绑定时取消尚未开始删除或租约仍可撤销的任务。 */
    @Update("UPDATE file_delete_task SET status = 'CANCELED', lease_owner = NULL, lease_until = NULL, "
            + "finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP "
            + "WHERE asset_id = #{assetId} AND status IN ('WAITING', 'PROCESSING')")
    int cancelRebindableTasks(@Param("assetId") Long assetId);
}
