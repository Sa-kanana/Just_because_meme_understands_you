package com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserTagFollowMapper {
    @Select("SELECT tag_id FROM user_tag_follow WHERE user_id = #{userId} ORDER BY create_time DESC, tag_id DESC")
    List<Long> selectFollowedTagIds(@Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO user_tag_follow (user_id, tag_id) VALUES (#{userId}, #{tagId})")
    int insertIfAbsent(@Param("userId") Long userId, @Param("tagId") Integer tagId);

    @Delete("DELETE FROM user_tag_follow WHERE user_id = #{userId} AND tag_id = #{tagId}")
    int deleteFollow(@Param("userId") Long userId, @Param("tagId") Integer tagId);
}
