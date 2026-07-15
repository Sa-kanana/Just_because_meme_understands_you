package com.sakana.just_because_meme_understands_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.entity.UserStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserStatsMapper extends BaseMapper<UserStats> {

    @Update("UPDATE user_stats SET follow_count = IFNULL(follow_count, 0) + 1 WHERE user_id = #{userId}")
    int incrementFollowCount(@Param("userId") long userId);

    @Update("UPDATE user_stats SET follow_count = GREATEST(IFNULL(follow_count, 0) - 1, 0) WHERE user_id = #{userId}")
    int decrementFollowCount(@Param("userId") long userId);

    @Update("UPDATE user_stats SET fans_count = IFNULL(fans_count, 0) + 1 WHERE user_id = #{userId}")
    int incrementFansCount(@Param("userId") long userId);

    @Update("UPDATE user_stats SET fans_count = GREATEST(IFNULL(fans_count, 0) - 1, 0) WHERE user_id = #{userId}")
    int decrementFansCount(@Param("userId") long userId);
}
