package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileOrphanCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 中文注释：OSS 孤儿候选记录的持久化映射。 */
@Mapper
public interface FileOrphanCandidateMapper extends BaseMapper<FileOrphanCandidate> {

    /** 中文注释：按对象键锁定孤儿候选，避免多实例重复安排删除任务。 */
    @Select("SELECT * FROM file_orphan_candidate WHERE object_key = #{objectKey} FOR UPDATE")
    FileOrphanCandidate selectByObjectKeyForUpdate(@Param("objectKey") String objectKey);

    /** 中文注释：返回指定状态的候选数，供每日对账汇总。 */
    @Select("SELECT COUNT(*) FROM file_orphan_candidate WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}
