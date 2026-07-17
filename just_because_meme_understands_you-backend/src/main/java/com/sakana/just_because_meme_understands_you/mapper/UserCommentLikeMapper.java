package com.sakana.just_because_meme_understands_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.entity.UserCommentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserCommentLikeMapper extends BaseMapper<UserCommentLike> {

    @Select("""
            <script>
            SELECT comment_id
            FROM user_comment_like
            WHERE user_id = #{userId}
              AND is_deleted = 0
              AND comment_id IN
              <foreach collection="commentIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<Long> selectLikedCommentIds(@Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);
}
