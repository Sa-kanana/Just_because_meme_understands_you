package com.sakana.just_because_meme_understands_you.modules.user.application.favorite.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.FavoriteFolderVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeFavoriteVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserFavoriteFolder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 用户收藏与收藏夹的 API 视图映射器。 */
@Mapper(config = MapStructConfig.class)
public interface UserFavoriteViewMapper {

    /** 将持久化收藏夹映射为非默认收藏夹视图。 */
    @Mapping(target = "coverUrl", ignore = true)
    @Mapping(target = "isDefault", constant = "false")
    FavoriteFolderVO toFavoriteFolderVO(UserFavoriteFolder source);

    /** 将收藏记录的主键映射到 API 的 favoriteId 字段。 */
    @Mapping(target = "favoriteId", source = "id")
    MemeFavoriteVO toMemeFavoriteVO(UserFavorite source);
}
