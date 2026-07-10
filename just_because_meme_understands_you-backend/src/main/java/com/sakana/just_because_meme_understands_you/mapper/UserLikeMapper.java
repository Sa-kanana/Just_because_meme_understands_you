package com.sakana.just_because_meme_understands_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.entity.UserLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserLikeMapper extends BaseMapper<UserLike> {

    @Select("""
            <script>
            SELECT meme_id
            FROM user_like
            WHERE user_id = #{userId}
              AND is_deleted = 0
              AND meme_id IN
              <foreach collection="memeIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<Long> selectLikedMemeIds(@Param("userId") Long userId, @Param("memeIds") List<Long> memeIds);
}
