package com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserFavoriteFolder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFavoriteFolderMapper extends BaseMapper<UserFavoriteFolder> {
}
