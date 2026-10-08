package com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper;

import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签 Mapper
 *
 * @author sakana
 * @since 2026-03-06
 */
@Mapper
public interface MemeTagMapper extends BaseMapper<MemeTag> {

}
