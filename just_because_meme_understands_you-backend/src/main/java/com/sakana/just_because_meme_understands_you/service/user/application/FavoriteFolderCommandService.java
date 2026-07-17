package com.sakana.just_because_meme_understands_you.service.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderCreateDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderReorderDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderUpdateDTO;
import com.sakana.just_because_meme_understands_you.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.entity.UserFavoriteFolder;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteFolderMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteMapper;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.service.user.assembler.FavoriteFolderAssembler;
import com.sakana.just_because_meme_understands_you.service.user.support.FavoriteFolderCacheSupport;
import com.sakana.just_because_meme_understands_you.service.user.support.FavoriteFolderCacheSupport.DefaultFolderMeta;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sakana.just_because_meme_understands_you.common.constant.DataStatusConstants.DELETED;
import static com.sakana.just_because_meme_understands_you.common.constant.DataStatusConstants.NOT_DELETED;

/**
 * Favorite folder write model.
 */
@Service
public class FavoriteFolderCommandService {

    private static final int SORT_STEP = 1000;

    @Resource
    private UserFavoriteFolderMapper folderMapper;

    @Resource
    private UserFavoriteMapper favoriteMapper;

    @Resource
    private FavoriteFolderCacheSupport folderCache;

    @Resource
    private FavoriteFolderAssembler folderAssembler;

    @Transactional(rollbackFor = Exception.class)
    public FavoriteFolderVO createFolder(Long userId, FavoriteFolderCreateDTO request) {
        requireUserId(userId);
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称不能为空");
        }
        String name = request.getName().trim();
        if (name.length() > IUserFavoriteFolderService.MAX_NAME_LENGTH) {
            throw new BizException(Result.CODE_BAD_REQUEST,
                    "收藏夹名称最长 " + IUserFavoriteFolderService.MAX_NAME_LENGTH + " 字");
        }
        String desc = request.getDescription() == null ? null : request.getDescription().trim();
        if (desc != null && desc.length() > IUserFavoriteFolderService.MAX_DESC_LENGTH) {
            throw new BizException(Result.CODE_BAD_REQUEST,
                    "简介最长 " + IUserFavoriteFolderService.MAX_DESC_LENGTH + " 字");
        }
        int isPublic = request.getIsPublic() == null ? 1 : (request.getIsPublic() == 0 ? 0 : 1);

        long count = folderMapper.selectCount(
                new LambdaQueryWrapper<UserFavoriteFolder>()
                        .eq(UserFavoriteFolder::getUserId, userId)
                        .eq(UserFavoriteFolder::getIsDeleted, NOT_DELETED));
        if (count >= IUserFavoriteFolderService.MAX_FOLDER_COUNT) {
            throw new BizException(Result.CODE_BAD_REQUEST,
                    "收藏夹数量已达上限 " + IUserFavoriteFolderService.MAX_FOLDER_COUNT);
        }
        if (isFolderNameTaken(userId, name, null)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        UserFavoriteFolder folder = new UserFavoriteFolder();
        folder.setUserId(userId);
        folder.setName(name);
        folder.setDescription(desc);
        folder.setIsPublic(isPublic);
        folder.setSortOrder(nextFolderSortOrder(userId));
        folder.setMemeCount(0);
        folder.setIsDeleted(NOT_DELETED);
        folder.setCreateTime(now);
        folder.setUpdateTime(now);
        folderMapper.insert(folder);

        folderCache.evictFolders(userId);
        return folderAssembler.toVO(folder);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateFolder(Long userId, Long folderId, FavoriteFolderUpdateDTO request) {
        requireUserId(userId);
        if (folderId == null || folderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求参数不能为空");
        }
        if (folderId == IUserFavoriteFolderService.DEFAULT_FOLDER_ID) {
            updateDefaultFolder(userId, request);
            return;
        }
        UserFavoriteFolder folder = loadOwnedFolder(userId, folderId);

        if (StringUtils.hasText(request.getName())) {
            String name = request.getName().trim();
            if (name.length() > IUserFavoriteFolderService.MAX_NAME_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST,
                        "收藏夹名称最长 " + IUserFavoriteFolderService.MAX_NAME_LENGTH + " 字");
            }
            if (!Objects.equals(folder.getName(), name)) {
                if (isFolderNameTaken(userId, name, folder.getId())) {
                    throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称已存在");
                }
                folder.setName(name);
            }
        }
        if (request.getDescription() != null) {
            String desc = request.getDescription().trim();
            if (desc.length() > IUserFavoriteFolderService.MAX_DESC_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST,
                        "简介最长 " + IUserFavoriteFolderService.MAX_DESC_LENGTH + " 字");
            }
            folder.setDescription(desc.isEmpty() ? null : desc);
        }
        if (request.getIsPublic() != null) {
            folder.setIsPublic(request.getIsPublic() == 0 ? 0 : 1);
        }
        folder.setUpdateTime(LocalDateTime.now());
        folderMapper.updateById(folder);
        folderCache.evictFolders(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(Long userId, Long folderId) {
        requireUserId(userId);
        if (folderId == null || folderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }
        if (folderId == IUserFavoriteFolderService.DEFAULT_FOLDER_ID) {
            throw new BizException(Result.CODE_BAD_REQUEST, "默认收藏夹不可删除");
        }
        UserFavoriteFolder folder = loadOwnedFolder(userId, folderId);

        List<UserFavorite> inFolder = favoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getFolderId, folderId)
                        .eq(UserFavorite::getIsDeleted, NOT_DELETED));
        if (!inFolder.isEmpty()) {
            int nextSort = nextTopSortOrder(userId, IUserFavoriteFolderService.DEFAULT_FOLDER_ID);
            for (UserFavorite favorite : inFolder) {
                favorite.setFolderId(IUserFavoriteFolderService.DEFAULT_FOLDER_ID);
                favorite.setSortOrder(nextSort);
                nextSort -= SORT_STEP;
                favorite.setUpdateTime(LocalDateTime.now());
                favoriteMapper.updateById(favorite);
            }
        }

        folder.setIsDeleted(DELETED);
        folder.setMemeCount(0);
        folder.setUpdateTime(LocalDateTime.now());
        folderMapper.updateById(folder);
        folderCache.evictFolders(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorderFolders(Long userId, FavoriteFolderReorderDTO request) {
        requireUserId(userId);
        if (request == null || request.getFolderIds() == null || request.getFolderIds().isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "排序项不能为空");
        }
        List<Long> ids = request.getFolderIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "排序项不能为空");
        }
        Set<Long> ownedIds = new HashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                ownedIds.add(id);
            }
        }
        if (!ownedIds.isEmpty()) {
            List<UserFavoriteFolder> owned = folderMapper.selectList(
                    new LambdaQueryWrapper<UserFavoriteFolder>()
                            .eq(UserFavoriteFolder::getUserId, userId)
                            .eq(UserFavoriteFolder::getIsDeleted, NOT_DELETED)
                            .in(UserFavoriteFolder::getId, ownedIds));
            Set<Long> existIds = owned.stream().map(UserFavoriteFolder::getId).collect(Collectors.toSet());
            for (Long id : ownedIds) {
                if (!existIds.contains(id)) {
                    throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹不存在: " + id);
                }
            }
        }
        LocalDateTime now = LocalDateTime.now();
        int order = 0;
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            folderMapper.update(null, new LambdaUpdateWrapper<UserFavoriteFolder>()
                    .eq(UserFavoriteFolder::getId, id)
                    .eq(UserFavoriteFolder::getUserId, userId)
                    .set(UserFavoriteFolder::getSortOrder, order)
                    .set(UserFavoriteFolder::getUpdateTime, now));
            order++;
        }
        folderCache.evictFolders(userId);
    }

    public void assertFolderOwnedByUser(Long userId, long folderId) {
        if (folderId == IUserFavoriteFolderService.DEFAULT_FOLDER_ID) {
            return;
        }
        if (folderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }
        loadOwnedFolder(userId, folderId);
    }

    public void adjustMemeCount(long folderId, int delta) {
        if (folderId == IUserFavoriteFolderService.DEFAULT_FOLDER_ID || delta == 0) {
            return;
        }
        UserFavoriteFolder folder = folderMapper.selectById(folderId);
        if (folder == null || (folder.getIsDeleted() != null && folder.getIsDeleted() == DELETED)) {
            return;
        }
        int current = folder.getMemeCount() == null ? 0 : folder.getMemeCount();
        folder.setMemeCount(Math.max(0, current + delta));
        folder.setUpdateTime(LocalDateTime.now());
        folderMapper.updateById(folder);
    }

    public void evictFolderCache(Long userId) {
        folderCache.evictFolders(userId);
    }

    private void updateDefaultFolder(Long userId, FavoriteFolderUpdateDTO request) {
        DefaultFolderMeta meta = folderCache.readDefaultMeta(userId);
        if (meta == null) {
            meta = new DefaultFolderMeta();
        }
        if (StringUtils.hasText(request.getName())) {
            String name = request.getName().trim();
            if (name.length() > IUserFavoriteFolderService.MAX_NAME_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST,
                        "收藏夹名称最长 " + IUserFavoriteFolderService.MAX_NAME_LENGTH + " 字");
            }
            if (!Objects.equals(folderAssembler.resolveDefaultFolderName(meta), name)
                    && isCustomFolderNameTaken(userId, name, null)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称已存在");
            }
            meta.name = name;
        }
        if (request.getDescription() != null) {
            String desc = request.getDescription().trim();
            if (desc.length() > IUserFavoriteFolderService.MAX_DESC_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST,
                        "简介最长 " + IUserFavoriteFolderService.MAX_DESC_LENGTH + " 字");
            }
            meta.description = desc.isEmpty() ? null : desc;
        }
        if (request.getIsPublic() != null) {
            meta.isPublic = request.getIsPublic() == 0 ? 0 : 1;
        }
        folderCache.saveDefaultMeta(userId, meta);
        folderCache.evictFolders(userId);
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }

    private UserFavoriteFolder loadOwnedFolder(Long userId, Long folderId) {
        UserFavoriteFolder folder = folderMapper.selectById(folderId);
        if (folder == null
                || !Objects.equals(folder.getUserId(), userId)
                || folder.getIsDeleted() == null
                || folder.getIsDeleted() == DELETED) {
            throw new BizException(Result.CODE_NOT_FOUND, "收藏夹不存在");
        }
        return folder;
    }

    private int nextFolderSortOrder(Long userId) {
        UserFavoriteFolder tail = folderMapper.selectOne(
                new LambdaQueryWrapper<UserFavoriteFolder>()
                        .eq(UserFavoriteFolder::getUserId, userId)
                        .eq(UserFavoriteFolder::getIsDeleted, NOT_DELETED)
                        .orderByDesc(UserFavoriteFolder::getSortOrder)
                        .last("LIMIT 1"));
        if (tail == null || tail.getSortOrder() == null) {
            return 0;
        }
        return tail.getSortOrder() + 1;
    }

    private int nextTopSortOrder(Long userId, long folderId) {
        UserFavorite top = favoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getFolderId, folderId)
                        .eq(UserFavorite::getIsDeleted, NOT_DELETED)
                        .orderByAsc(UserFavorite::getSortOrder)
                        .last("LIMIT 1"));
        if (top == null || top.getSortOrder() == null) {
            return SORT_STEP;
        }
        return top.getSortOrder() - SORT_STEP;
    }

    private boolean isFolderNameTaken(Long userId, String name, Long excludeFolderId) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        String trimmed = name.trim();
        DefaultFolderMeta meta = folderCache.readDefaultMeta(userId);
        if (Objects.equals(folderAssembler.resolveDefaultFolderName(meta), trimmed)) {
            return true;
        }
        return isCustomFolderNameTaken(userId, trimmed, excludeFolderId);
    }

    private boolean isCustomFolderNameTaken(Long userId, String name, Long excludeFolderId) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        LambdaQueryWrapper<UserFavoriteFolder> wrapper = new LambdaQueryWrapper<UserFavoriteFolder>()
                .eq(UserFavoriteFolder::getUserId, userId)
                .eq(UserFavoriteFolder::getName, name.trim())
                .eq(UserFavoriteFolder::getIsDeleted, NOT_DELETED);
        if (excludeFolderId != null) {
            wrapper.ne(UserFavoriteFolder::getId, excludeFolderId);
        }
        return folderMapper.selectCount(wrapper) > 0;
    }
}
