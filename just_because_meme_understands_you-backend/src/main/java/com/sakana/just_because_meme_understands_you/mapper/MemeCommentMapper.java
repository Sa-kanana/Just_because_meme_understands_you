package com.sakana.just_because_meme_understands_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.entity.MemeComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 梗评论 Mapper
 *
 * @author sakana
 */
@Mapper
public interface MemeCommentMapper extends BaseMapper<MemeComment> {
}
