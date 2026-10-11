package com.sakana.just_because_meme_understands_you.modules.user.application.favorite.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserFavoriteMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.modules.user.application.favorite.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.api.user.vo.FavoriteFolderVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sakana.just_because_meme_understands_you.common.data.DataStatusConstants.NOT_DELETED;

/**
 * Resolve folder cover images from the latest favorite in each folder.
 */
@Component
public class FavoriteFolderCoverResolver {

    @Resource
    private UserFavoriteMapper favoriteMapper;

    @Resource
    private MemeCatalogPort memeCatalogPort;

    @Resource
    private OssUrlHelper ossUrlHelper;

    public void fillCovers(Long userId, List<FavoriteFolderVO> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        Map<Long, Long> folderToMemeId = new HashMap<>();
        for (FavoriteFolderVO vo : voList) {
            long folderId = vo.getId() == null ? IUserFavoriteFolderService.DEFAULT_FOLDER_ID : vo.getId();
            UserFavorite latest = favoriteMapper.selectOne(
                    new LambdaQueryWrapper<UserFavorite>()
                            .eq(UserFavorite::getUserId, userId)
                            .eq(UserFavorite::getFolderId, folderId)
                            .eq(UserFavorite::getIsDeleted, NOT_DELETED)
                            .orderByDesc(UserFavorite::getCreateTime)
                            .orderByDesc(UserFavorite::getId)
                            .last("LIMIT 1"));
            if (latest != null && latest.getMemeId() != null) {
                folderToMemeId.put(folderId, latest.getMemeId());
            }
        }
        if (folderToMemeId.isEmpty()) {
            return;
        }
        List<MemeCatalogPort.MemeCard> memes = memeCatalogPort.findByIds(folderToMemeId.values());
        Map<Long, String> idToImage = new HashMap<>();
        for (MemeCatalogPort.MemeCard meme : memes) {
            if (meme.id() != null) {
                idToImage.put(meme.id(), meme.image());
            }
        }
        for (FavoriteFolderVO vo : voList) {
            long folderId = vo.getId() == null ? IUserFavoriteFolderService.DEFAULT_FOLDER_ID : vo.getId();
            Long memeId = folderToMemeId.get(folderId);
            if (memeId != null) {
                vo.setCoverUrl(ossUrlHelper.toPublicUrl(idToImage.get(memeId)));
            }
        }
    }
}
