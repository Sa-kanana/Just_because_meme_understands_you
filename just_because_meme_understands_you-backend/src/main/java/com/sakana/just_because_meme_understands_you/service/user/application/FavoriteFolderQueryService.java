package com.sakana.just_because_meme_understands_you.service.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.entity.UserFavoriteFolder;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteFolderMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteMapper;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.service.user.assembler.FavoriteFolderAssembler;
import com.sakana.just_because_meme_understands_you.service.user.support.FavoriteFolderCacheSupport;
import com.sakana.just_because_meme_understands_you.service.user.support.FavoriteFolderCacheSupport.DefaultFolderMeta;
import com.sakana.just_because_meme_understands_you.service.user.support.FavoriteFolderCoverResolver;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderListVO;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.sakana.just_because_meme_understands_you.common.constant.DataStatusConstants.NOT_DELETED;

/**
 * Favorite folder read model.
 */
@Service
public class FavoriteFolderQueryService {

    @Resource
    private UserFavoriteFolderMapper folderMapper;

    @Resource
    private UserFavoriteMapper favoriteMapper;

    @Resource
    private FavoriteFolderCacheSupport folderCache;

    @Resource
    private FavoriteFolderAssembler folderAssembler;

    @Resource
    private FavoriteFolderCoverResolver coverResolver;

    public FavoriteFolderListVO listMyFolders(Long userId) {
        return listFolders(userId, userId);
    }

    public FavoriteFolderListVO listFolders(Long targetUserId, Long currentUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不合法");
        }
        boolean isOwner = currentUserId != null && currentUserId.equals(targetUserId);

        String cacheKey = folderCache.foldersKey(targetUserId, isOwner);
        FavoriteFolderListVO cached = folderCache.get(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            if (!isOwner) {
                folderAssembler.filterPrivateAndDefault(cached);
            }
            return cached;
        }

        List<UserFavoriteFolder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<UserFavoriteFolder>()
                        .eq(UserFavoriteFolder::getUserId, targetUserId)
                        .eq(UserFavoriteFolder::getIsDeleted, NOT_DELETED)
                        .orderByAsc(UserFavoriteFolder::getSortOrder)
                        .orderByDesc(UserFavoriteFolder::getCreateTime));

        long defaultCount = favoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, targetUserId)
                        .eq(UserFavorite::getFolderId, IUserFavoriteFolderService.DEFAULT_FOLDER_ID)
                        .eq(UserFavorite::getIsDeleted, NOT_DELETED));

        List<FavoriteFolderVO> voList = new ArrayList<>();
        FavoriteFolderVO defaultVo = folderAssembler.buildDefaultFolderVo(
                (int) defaultCount, folderCache.readDefaultMeta(targetUserId));
        if (isOwner || isDefaultFolderPublic(targetUserId)) {
            voList.add(defaultVo);
        }
        for (UserFavoriteFolder folder : folders) {
            voList.add(folderAssembler.toVO(folder));
        }
        coverResolver.fillCovers(targetUserId, voList);

        FavoriteFolderListVO listVO = new FavoriteFolderListVO();
        listVO.setFolders(voList);
        listVO.setTotal(voList.size());

        if (isOwner) {
            folderCache.put(cacheKey, listVO);
            return listVO;
        }

        FavoriteFolderListVO fullVO = new FavoriteFolderListVO();
        fullVO.setFolders(voList);
        fullVO.setTotal(voList.size());
        folderCache.put(cacheKey, fullVO);
        folderAssembler.filterPrivateAndDefault(fullVO);
        return fullVO;
    }

    public boolean isDefaultFolderPublic(Long userId) {
        DefaultFolderMeta meta = folderCache.readDefaultMeta(userId);
        return meta == null || meta.isPublic == null || meta.isPublic == 1;
    }
}
