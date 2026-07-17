package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderCreateDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderReorderDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderUpdateDTO;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.service.user.application.FavoriteFolderCommandService;
import com.sakana.just_because_meme_understands_you.service.user.application.FavoriteFolderQueryService;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderListVO;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderVO;
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
