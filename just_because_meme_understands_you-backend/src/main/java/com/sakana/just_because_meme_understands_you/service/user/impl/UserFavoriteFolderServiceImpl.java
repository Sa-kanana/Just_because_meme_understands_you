package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderCreateDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderReorderDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderUpdateDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.entity.UserFavoriteFolder;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteFolderMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteMapper;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderListVO;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserFavoriteFolderServiceImpl implements IUserFavoriteFolderService {

    private static final String FOLDERS_CACHE_PREFIX = "user:folders:";
    private static final String DEFAULT_META_PREFIX = "user:favorite-folder:default-meta:";
    private static final String DEFAULT_FOLDER_NAME = "默认收藏夹";
    private static final long CACHE_MINUTES = 5L;
    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int SORT_STEP = 1000;

    @Resource
    private UserFavoriteFolderMapper folderMapper;

    @Resource
    private UserFavoriteMapper favoriteMapper;

    @Resource
    private IMemeService memeService;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public FavoriteFolderListVO listMyFolders(Long userId) {
        return listFolders(userId, userId);
    }

    @Override
    public FavoriteFolderListVO listFolders(Long targetUserId, Long currentUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不合法");
        }
        boolean isOwner = currentUserId != null && currentUserId.equals(targetUserId);

        String cacheKey = FOLDERS_CACHE_PREFIX + targetUserId + ":" + (isOwner ? "self" : "guest");
        FavoriteFolderListVO cached = readCache(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            if (!isOwner) {
                filterPrivateAndDefault(cached);
            }
            return cached;
        }

        List<UserFavoriteFolder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<UserFavoriteFolder>()
                        .eq(UserFavoriteFolder::getUserId, targetUserId)
                        .eq(UserFavoriteFolder::getIsDeleted, NOT_DELETED)
                        .orderByAsc(UserFavoriteFolder::getSortOrder)
                        .orderByDesc(UserFavoriteFolder::getCreateTime));

        // 默认夹收藏数
        long defaultCount = favoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, targetUserId)
                        .eq(UserFavorite::getFolderId, DEFAULT_FOLDER_ID)
                        .eq(UserFavorite::getIsDeleted, NOT_DELETED));

        List<FavoriteFolderVO> voList = new ArrayList<>();
        FavoriteFolderVO defaultVo = buildDefaultFolderVo((int) defaultCount, targetUserId);
        if (isOwner || isDefaultFolderPublic(targetUserId)) {
            voList.add(defaultVo);
        }
        for (UserFavoriteFolder folder : folders) {
            voList.add(toVO(folder));
        }
        fillCovers(targetUserId, voList);

        FavoriteFolderListVO listVO = new FavoriteFolderListVO();
        listVO.setFolders(voList);
        listVO.setTotal(voList.size());

        if (isOwner) {
            writeCache(cacheKey, listVO);
            return listVO;
        }
        // 他人仅公开自定义夹，不缓存过滤后结果（缓存完整列表，返回前过滤）
        FavoriteFolderListVO fullVO = new FavoriteFolderListVO();
        fullVO.setFolders(voList);
        fullVO.setTotal(voList.size());
        writeCache(cacheKey, fullVO);
        filterPrivateAndDefault(fullVO);
        return fullVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteFolderVO createFolder(Long userId, FavoriteFolderCreateDTO request) {
        requireUserId(userId);
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称不能为空");
        }
        String name = request.getName().trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称最长 " + MAX_NAME_LENGTH + " 字");
        }
        String desc = request.getDescription() == null ? null : request.getDescription().trim();
        if (desc != null && desc.length() > MAX_DESC_LENGTH) {
            throw new BizException(Result.CODE_BAD_REQUEST, "简介最长 " + MAX_DESC_LENGTH + " 字");
        }
        int isPublic = request.getIsPublic() == null ? 1 : (request.getIsPublic() == 0 ? 0 : 1);

        long count = folderMapper.selectCount(
                new LambdaQueryWrapper<UserFavoriteFolder>()
                        .eq(UserFavoriteFolder::getUserId, userId)
                        .eq(UserFavoriteFolder::getIsDeleted, NOT_DELETED));
        if (count >= MAX_FOLDER_COUNT) {
            throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹数量已达上限 " + MAX_FOLDER_COUNT);
        }
        if (isFolderNameTaken(userId, name, null)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称已存在");
        }

        int sortOrder = nextFolderSortOrder(userId);
        LocalDateTime now = LocalDateTime.now();
        UserFavoriteFolder folder = new UserFavoriteFolder();
        folder.setUserId(userId);
        folder.setName(name);
        folder.setDescription(desc);
        folder.setIsPublic(isPublic);
        folder.setSortOrder(sortOrder);
        folder.setMemeCount(0);
        folder.setIsDeleted(NOT_DELETED);
        folder.setCreateTime(now);
        folder.setUpdateTime(now);
        folderMapper.insert(folder);

        evictFolderCache(userId);
        return toVO(folder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFolder(Long userId, Long folderId, FavoriteFolderUpdateDTO request) {
        requireUserId(userId);
        if (folderId == null || folderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求参数不能为空");
        }
        if (folderId == DEFAULT_FOLDER_ID) {
            updateDefaultFolder(userId, request);
            return;
        }
        UserFavoriteFolder folder = loadOwnedFolder(userId, folderId);

        if (StringUtils.hasText(request.getName())) {
            String name = request.getName().trim();
            if (name.length() > MAX_NAME_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称最长 " + MAX_NAME_LENGTH + " 字");
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
            if (desc.length() > MAX_DESC_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST, "简介最长 " + MAX_DESC_LENGTH + " 字");
            }
            folder.setDescription(desc.isEmpty() ? null : desc);
        }
        if (request.getIsPublic() != null) {
            folder.setIsPublic(request.getIsPublic() == 0 ? 0 : 1);
        }
        folder.setUpdateTime(LocalDateTime.now());
        folderMapper.updateById(folder);
        evictFolderCache(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(Long userId, Long folderId) {
        requireUserId(userId);
        if (folderId == null || folderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }
        if (folderId == DEFAULT_FOLDER_ID) {
            throw new BizException(Result.CODE_BAD_REQUEST, "默认收藏夹不可删除");
        }
        UserFavoriteFolder folder = loadOwnedFolder(userId, folderId);

        // 1. 夹内收藏移入默认夹
        List<UserFavorite> inFolder = favoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getFolderId, folderId)
                        .eq(UserFavorite::getIsDeleted, NOT_DELETED));
        if (!inFolder.isEmpty()) {
            int nextSort = nextTopSortOrder(userId, DEFAULT_FOLDER_ID);
            for (UserFavorite fav : inFolder) {
                fav.setFolderId(DEFAULT_FOLDER_ID);
                fav.setSortOrder(nextSort);
                nextSort -= SORT_STEP;
                fav.setUpdateTime(LocalDateTime.now());
                favoriteMapper.updateById(fav);
            }
        }

        // 2. 软删收藏夹，计数归零
        folder.setIsDeleted(DELETED);
        folder.setMemeCount(0);
        folder.setUpdateTime(LocalDateTime.now());
        folderMapper.updateById(folder);

        evictFolderCache(userId);
    }

    @Override
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
        evictFolderCache(userId);
    }

    @Override
    public void assertFolderOwnedByUser(Long userId, long folderId) {
        if (folderId == DEFAULT_FOLDER_ID) {
            return;
        }
        if (folderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }
        loadOwnedFolder(userId, folderId);
    }

    @Override
    public void adjustMemeCount(long folderId, int delta) {
        if (folderId == DEFAULT_FOLDER_ID) {
            return;
        }
        if (delta == 0) {
            return;
        }
        UserFavoriteFolder folder = folderMapper.selectById(folderId);
        if (folder == null || folder.getIsDeleted() != null && folder.getIsDeleted() == DELETED) {
            return;
        }
        int current = folder.getMemeCount() == null ? 0 : folder.getMemeCount();
        int next = Math.max(0, current + delta);
        folder.setMemeCount(next);
        folder.setUpdateTime(LocalDateTime.now());
        folderMapper.updateById(folder);
    }

    @Override
    public boolean isDefaultFolderPublic(Long userId) {
        DefaultFolderMeta meta = readDefaultMeta(userId);
        return meta == null || meta.isPublic == null || meta.isPublic == 1;
    }

    @Override
    public void evictFolderCache(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        try {
            Set<String> keys = stringRedisTemplate.keys(FOLDERS_CACHE_PREFIX + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== private helpers ====================

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

    /**
     * 夹内新收藏置于顶部：取当前最小 sortOrder - step。
     */
    int nextTopSortOrder(Long userId, long folderId) {
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

    private FavoriteFolderVO buildDefaultFolderVo(int memeCount, Long userId) {
        DefaultFolderMeta meta = readDefaultMeta(userId);
        FavoriteFolderVO vo = new FavoriteFolderVO();
        vo.setId(DEFAULT_FOLDER_ID);
        vo.setName(resolveDefaultFolderName(meta));
        vo.setDescription(meta != null ? meta.description : null);
        vo.setIsPublic(meta != null && meta.isPublic != null ? meta.isPublic : 1);
        vo.setSortOrder(-1);
        vo.setMemeCount(memeCount);
        vo.setIsDefault(true);
        return vo;
    }

    private void updateDefaultFolder(Long userId, FavoriteFolderUpdateDTO request) {
        DefaultFolderMeta meta = readDefaultMeta(userId);
        if (meta == null) {
            meta = new DefaultFolderMeta();
        }
        if (StringUtils.hasText(request.getName())) {
            String name = request.getName().trim();
            if (name.length() > MAX_NAME_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称最长 " + MAX_NAME_LENGTH + " 字");
            }
            if (!Objects.equals(resolveDefaultFolderName(meta), name)) {
                if (isCustomFolderNameTaken(userId, name, null)) {
                    throw new BizException(Result.CODE_BAD_REQUEST, "收藏夹名称已存在");
                }
            }
            meta.name = name;
        }
        if (request.getDescription() != null) {
            String desc = request.getDescription().trim();
            if (desc.length() > MAX_DESC_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST, "简介最长 " + MAX_DESC_LENGTH + " 字");
            }
            meta.description = desc.isEmpty() ? null : desc;
        }
        if (request.getIsPublic() != null) {
            meta.isPublic = request.getIsPublic() == 0 ? 0 : 1;
        }
        saveDefaultMeta(userId, meta);
        evictFolderCache(userId);
    }

    private boolean isFolderNameTaken(Long userId, String name, Long excludeFolderId) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        String trimmed = name.trim();
        DefaultFolderMeta meta = readDefaultMeta(userId);
        if (Objects.equals(resolveDefaultFolderName(meta), trimmed)) {
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

    private String resolveDefaultFolderName(DefaultFolderMeta meta) {
        if (meta != null && StringUtils.hasText(meta.name)) {
            return meta.name.trim();
        }
        return DEFAULT_FOLDER_NAME;
    }

    private DefaultFolderMeta readDefaultMeta(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(DEFAULT_META_PREFIX + userId);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, DefaultFolderMeta.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveDefaultMeta(Long userId, DefaultFolderMeta meta) {
        try {
            stringRedisTemplate.opsForValue().set(DEFAULT_META_PREFIX + userId, objectMapper.writeValueAsString(meta));
        } catch (Exception e) {
            throw new BizException(Result.CODE_ERROR, "保存默认收藏夹信息失败");
        }
    }

    private static class DefaultFolderMeta {
        public String name;
        public String description;
        public Integer isPublic;
    }

    private FavoriteFolderVO toVO(UserFavoriteFolder folder) {
        FavoriteFolderVO vo = new FavoriteFolderVO();
        vo.setId(folder.getId());
        vo.setName(folder.getName());
        vo.setDescription(folder.getDescription());
        vo.setIsPublic(folder.getIsPublic());
        vo.setSortOrder(folder.getSortOrder());
        vo.setMemeCount(folder.getMemeCount());
        vo.setIsDefault(false);
        vo.setCreateTime(folder.getCreateTime());
        vo.setUpdateTime(folder.getUpdateTime());
        return vo;
    }

    /**
     * 动态填充封面：取每个夹内「最后收藏」的一条梗图作为封面。默认夹同样处理。
     * 不单独存储封面字段，跟随收藏内容自动变化。
     */
    private void fillCovers(Long userId, List<FavoriteFolderVO> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        // 每个夹取一条最后收藏（create_time 最大）的 meme_id
        Map<Long, Long> folderToMemeId = new HashMap<>();
        for (FavoriteFolderVO vo : voList) {
            long fid = vo.getId() == null ? DEFAULT_FOLDER_ID : vo.getId();
            UserFavorite latest = favoriteMapper.selectOne(
                    new LambdaQueryWrapper<UserFavorite>()
                            .eq(UserFavorite::getUserId, userId)
                            .eq(UserFavorite::getFolderId, fid)
                            .eq(UserFavorite::getIsDeleted, NOT_DELETED)
                            .orderByDesc(UserFavorite::getCreateTime)
                            .orderByDesc(UserFavorite::getId)
                            .last("LIMIT 1"));
            if (latest != null && latest.getMemeId() != null) {
                folderToMemeId.put(fid, latest.getMemeId());
            }
        }
        if (folderToMemeId.isEmpty()) {
            return;
        }
        List<Integer> memeIds = folderToMemeId.values().stream().map(Long::intValue).distinct().toList();
        List<Meme> memes = memeService.listByIds(memeIds);
        Map<Integer, String> idToImage = new HashMap<>();
        for (Meme m : memes) {
            if (m.getId() != null) {
                idToImage.put(m.getId(), m.getImage());
            }
        }
        for (FavoriteFolderVO vo : voList) {
            long fid = vo.getId() == null ? DEFAULT_FOLDER_ID : vo.getId();
            Long memeId = folderToMemeId.get(fid);
            if (memeId != null) {
                String image = idToImage.get(memeId.intValue());
                vo.setCoverUrl(ossUrlHelper.toPublicUrl(image));
            }
        }
    }

    private void filterPrivateAndDefault(FavoriteFolderListVO listVO) {
        if (listVO == null || listVO.getFolders() == null) {
            return;
        }
        List<FavoriteFolderVO> filtered = new ArrayList<>();
        for (FavoriteFolderVO vo : listVO.getFolders()) {
            if (vo.getId() != null && vo.getId() == DEFAULT_FOLDER_ID) {
                if (vo.getIsPublic() != null && vo.getIsPublic() == 1) {
                    filtered.add(vo);
                }
                continue;
            }
            if (vo.getIsPublic() != null && vo.getIsPublic() == 1) {
                filtered.add(vo);
            }
        }
        listVO.setFolders(filtered);
        listVO.setTotal(filtered.size());
    }

    private <T> T readCache(String key, TypeReference<T> typeReference) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeCache(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (StringUtils.hasText(json)) {
                stringRedisTemplate.opsForValue().set(key, json, CACHE_MINUTES, TimeUnit.MINUTES);
            }
        } catch (Exception ignored) {
        }
    }
}
