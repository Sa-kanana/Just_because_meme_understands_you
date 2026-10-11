package com.sakana.just_because_meme_understands_you.modules.user.application.favorite.impl;

import com.sakana.just_because_meme_understands_you.api.user.dto.FavoriteFolderCreateDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.FavoriteFolderReorderDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.FavoriteFolderUpdateDTO;
import com.sakana.just_because_meme_understands_you.modules.user.application.favorite.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.modules.user.application.favorite.usecase.FavoriteFolderCommandService;
import com.sakana.just_because_meme_understands_you.modules.user.application.favorite.usecase.FavoriteFolderQueryService;
import com.sakana.just_because_meme_understands_you.api.user.vo.FavoriteFolderListVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.FavoriteFolderVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Favorite folder facade. Delegates reads/writes to application services.
 */
@Service
public class UserFavoriteFolderServiceImpl implements IUserFavoriteFolderService {

    @Resource
    private FavoriteFolderQueryService queryService;

    @Resource
    private FavoriteFolderCommandService commandService;

    @Override
    public FavoriteFolderListVO listMyFolders(Long userId) {
        return queryService.listMyFolders(userId);
    }

    @Override
    public FavoriteFolderListVO listFolders(Long targetUserId, Long currentUserId) {
        return queryService.listFolders(targetUserId, currentUserId);
    }

    @Override
    public FavoriteFolderVO createFolder(Long userId, FavoriteFolderCreateDTO request) {
        return commandService.createFolder(userId, request);
    }

    @Override
    public void updateFolder(Long userId, Long folderId, FavoriteFolderUpdateDTO request) {
        commandService.updateFolder(userId, folderId, request);
    }

    @Override
    public void deleteFolder(Long userId, Long folderId) {
        commandService.deleteFolder(userId, folderId);
    }

    @Override
    public void reorderFolders(Long userId, FavoriteFolderReorderDTO request) {
        commandService.reorderFolders(userId, request);
    }

    @Override
    public void assertFolderOwnedByUser(Long userId, long folderId) {
        commandService.assertFolderOwnedByUser(userId, folderId);
    }

    @Override
    public void adjustMemeCount(long folderId, int delta) {
        commandService.adjustMemeCount(folderId, delta);
    }

    @Override
    public void evictFolderCache(Long userId) {
        commandService.evictFolderCache(userId);
    }

    @Override
    public boolean isDefaultFolderPublic(Long userId) {
        return queryService.isDefaultFolderPublic(userId);
    }
}
