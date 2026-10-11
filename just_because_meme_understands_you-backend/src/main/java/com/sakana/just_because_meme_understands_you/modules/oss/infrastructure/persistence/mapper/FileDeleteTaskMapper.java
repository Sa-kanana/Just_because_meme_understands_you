package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileDeleteTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 中文注释：OSS 延迟删除任务的持久化映射。 */
@Mapper
public interface FileDeleteTaskMapper extends BaseMapper<FileDeleteTask> {

    /**
     * 中文注释：在调用方事务中锁定到期任务；调用方必须立即写入 PROCESSING 状态和租约。
     * 已过期租约的任务允许重新领取，SKIP LOCKED 避免多实例重复锁定同一批记录。
     */
    @Select("SELECT * FROM file_delete_task "
            + "WHERE (status = 'WAITING' AND next_attempt_at <= #{now}) "
            + "OR (status = 'PROCESSING' AND lease_until <= #{now}) "
            + "ORDER BY next_attempt_at ASC, id ASC LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<FileDeleteTask> claimDueTasks(
            @Param("limit") int limit,
            @Param("now") LocalDateTime now);
}
