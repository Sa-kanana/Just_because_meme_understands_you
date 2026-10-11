package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileOrphanCandidate;
import org.apache.ibatis.annotations.Mapper;

/** 中文注释：OSS 孤儿候选记录的持久化映射。 */
@Mapper
public interface FileOrphanCandidateMapper extends BaseMapper<FileOrphanCandidate> {
}
