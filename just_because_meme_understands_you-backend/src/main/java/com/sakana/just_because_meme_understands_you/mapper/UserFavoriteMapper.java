package com.sakana.just_because_meme_understands_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
