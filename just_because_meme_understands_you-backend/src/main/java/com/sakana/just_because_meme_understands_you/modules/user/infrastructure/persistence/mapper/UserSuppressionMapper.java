package com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserSuppression;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserSuppressionMapper extends BaseMapper<UserSuppression> {
    @Select("SELECT COUNT(*) FROM user_suppression WHERE owner_user_id=#{ownerId} " +
            "AND target_user_id=#{targetId} AND type=#{type}")
    int countRelation(@Param("ownerId") Long ownerId, @Param("targetId") Long targetId,
                      @Param("type") String type);

    @Select("SELECT COUNT(*) FROM user_suppression WHERE type='BLOCK' AND " +
            "((owner_user_id=#{firstId} AND target_user_id=#{secondId}) OR " +
            "(owner_user_id=#{secondId} AND target_user_id=#{firstId}))")
    int existsBlockBetween(@Param("firstId") Long firstId, @Param("secondId") Long secondId);

    @Select("SELECT * FROM user_suppression WHERE owner_user_id=#{ownerId} ORDER BY created_at DESC, id DESC")
    List<UserSuppression> selectOwned(@Param("ownerId") Long ownerId);

    @Select("SELECT * FROM user_suppression WHERE target_user_id=#{targetId} AND type='BLOCK' " +
            "ORDER BY created_at DESC, id DESC")
    List<UserSuppression> selectBlocksTargeting(@Param("targetId") Long targetId);

    @Delete("DELETE FROM user_suppression WHERE owner_user_id=#{ownerId} " +
            "AND target_user_id=#{targetId} AND type=#{type}")
    int deleteOwned(@Param("ownerId") Long ownerId, @Param("targetId") Long targetId,
                    @Param("type") String type);
}
