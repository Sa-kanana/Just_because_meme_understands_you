package com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeCommentImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 梗评论图片 Mapper
 *
 * @author sakana
 */
@Mapper
public interface MemeCommentImageMapper extends BaseMapper<MemeCommentImage> {
}
