package com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 梗评论 Mapper
 *
 * @author sakana
 */
@Mapper
public interface MemeCommentMapper extends BaseMapper<MemeComment> {

    @Update("UPDATE meme_comment SET likes = IFNULL(likes, 0) + 1 WHERE id = #{commentId} AND is_deleted = 0")
    int incrementLikes(@Param("commentId") long commentId);

    @Update("UPDATE meme_comment SET likes = GREATEST(IFNULL(likes, 0) - 1, 0) WHERE id = #{commentId}")
    int decrementLikes(@Param("commentId") long commentId);

    @Update("UPDATE meme_comment SET is_deleted = 1, update_time = NOW() " +
            "WHERE id = #{commentId} AND is_deleted = 0")
    int hideById(@Param("commentId") Long commentId);
}
