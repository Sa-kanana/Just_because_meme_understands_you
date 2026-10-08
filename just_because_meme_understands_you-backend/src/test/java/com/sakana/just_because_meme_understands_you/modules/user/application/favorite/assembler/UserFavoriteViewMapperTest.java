package com.sakana.just_because_meme_understands_you.modules.user.application.favorite.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.FavoriteFolderVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeFavoriteVO;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserFavoriteFolder;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserFavoriteViewMapperTest {

    private final UserFavoriteViewMapper mapper = Mappers.getMapper(UserFavoriteViewMapper.class);

    @Test
    void mapsFolderFieldsAndMarksPersistedFolderAsNonDefault() {
        UserFavoriteFolder source = new UserFavoriteFolder();
        source.setId(7L);
        source.setName("收藏");
        source.setIsPublic(1);

        FavoriteFolderVO result = mapper.toFavoriteFolderVO(source);

        assertEquals(7L, result.getId());
        assertEquals("收藏", result.getName());
        assertFalse(result.getIsDefault());
        assertNull(result.getCoverUrl());
    }

    @Test
    void mapsFavoriteIdentityAndFolderFields() {
        UserFavorite source = new UserFavorite();
        source.setId(19L);
        source.setFolderId(7L);
        source.setSortOrder(2);

        MemeFavoriteVO result = mapper.toMemeFavoriteVO(source);

        assertEquals(19L, result.getFavoriteId());
        assertEquals(7L, result.getFolderId());
        assertEquals(2, result.getSortOrder());
    }
}
