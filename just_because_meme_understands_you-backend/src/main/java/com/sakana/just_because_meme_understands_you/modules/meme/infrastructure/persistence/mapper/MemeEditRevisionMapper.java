package com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeEditRevision;
import org.apache.ibatis.annotations.Mapper;

/** 中文注释：梗修改稿审核记录持久化映射。 */
@Mapper
public interface MemeEditRevisionMapper extends BaseMapper<MemeEditRevision> {
}
