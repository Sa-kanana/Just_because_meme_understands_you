package com.sakana.just_because_meme_understands_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.dto.UserFavoriteMemeJoinRow;
import com.sakana.just_because_meme_understands_you.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    @Select("""
            <script>
            SELECT
              f.id AS favorite_id,
              f.meme_id AS meme_id,
              f.folder_id AS folder_id,
              f.sort_order AS sort_order,
              f.create_time AS favorite_time,
              m.name AS name,
              m.image AS image,
              m.page_views AS page_views
            FROM user_favorite f
            INNER JOIN meme m ON m.id = f.meme_id
            WHERE f.user_id = #{userId}
              AND f.folder_id = #{folderId}
              AND f.is_deleted = 0
              AND m.status &lt;&gt; 4
              <if test="!isOwner">
                AND m.status = 1
              </if>
            ORDER BY f.sort_order DESC, f.create_time DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<UserFavoriteMemeJoinRow> selectVisibleFavoriteMemePage(@Param("userId") Long userId,
                                                                @Param("folderId") Long folderId,
                                                                @Param("isOwner") boolean isOwner,
                                                                @Param("offset") long offset,
                                                                @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM user_favorite f
            INNER JOIN meme m ON m.id = f.meme_id
            WHERE f.user_id = #{userId}
              AND f.folder_id = #{folderId}
              AND f.is_deleted = 0
              AND m.status &lt;&gt; 4
              <if test="!isOwner">
                AND m.status = 1
              </if>
            </script>
            """)
    long countVisibleFavoriteMemes(@Param("userId") Long userId,
                                   @Param("folderId") Long folderId,
                                   @Param("isOwner") boolean isOwner);
}
