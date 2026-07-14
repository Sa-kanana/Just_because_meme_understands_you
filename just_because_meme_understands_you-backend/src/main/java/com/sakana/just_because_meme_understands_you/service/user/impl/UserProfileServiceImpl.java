package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.dto.MemeTagBindDTO;
import com.sakana.just_because_meme_understands_you.dto.UserFavoriteMemeJoinRow;
import com.sakana.just_because_meme_understands_you.dto.UserProfileUpdateRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.entity.UserRelation;
import com.sakana.just_because_meme_understands_you.entity.UserStats;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserRelationMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserStatsMapper;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.service.user.IUserProfileService;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import com.sakana.just_because_meme_understands_you.service.user.UserFavoriteCountService;
import com.sakana.just_because_meme_understands_you.service.user.support.AuthorSupport;
import com.sakana.just_because_meme_understands_you.vo.AccountProfileVO;
import com.sakana.just_because_meme_understands_you.vo.AuthorVO;
import com.sakana.just_because_meme_understands_you.vo.EditProfileEchoVO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.UploadAvatarVO;
import com.sakana.just_because_meme_understands_you.vo.UserFavoriteItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemeItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemePageVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemeTagVO;
import com.sakana.just_because_meme_understands_you.vo.UserProfileStatsVO;
import com.sakana.just_because_meme_understands_you.vo.UserProfileVO;
import com.sakana.just_because_meme_understands_you.vo.UserPublishedMemeVO;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UserProfileServiceImpl implements IUserProfileService {

    private static final long CACHE_MINUTES = 5L;
    private static final String PROFILE_CACHE_PREFIX = "user:profile:";
    private static final String MEMES_CACHE_PREFIX = "user:memes:";
    private static final String FAVORITES_CACHE_PREFIX = "user:favorites:";
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;
    private static final int FAVORITE_NOT_DELETED = 0;

    @Resource
    private IUserService userService;

    @Resource
    private AuthorSupport authorSupport;

    @Resource
    private IMemeService memeService;

    @Resource
    private MemeTagRelationMapper memeTagRelationMapper;

    @Resource
    private UserStatsMapper userStatsMapper;

    @Resource
    private UserFavoriteMapper userFavoriteMapper;

    @Resource
    private com.sakana.just_because_meme_understands_you.mapper.UserFavoriteFolderMapper userFavoriteFolderMapper;

    @Resource
    private UserRelationMapper userRelationMapper;

    @Resource
    private OSS ossClient;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserFavoriteCountService userFavoriteCountService;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private IUserFavoriteFolderService userFavoriteFolderService;

    @Value("${oss.bucketName}")
    private String bucketName;

    @Override
    public UserProfileVO getUserProfile(Long targetUserId, Long currentUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不合法");
        }
        String cacheKey = PROFILE_CACHE_PREFIX + targetUserId + ":" + currentUserId;
        UserProfileVO cached = readCache(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            ossUrlHelper.refreshUserProfileUrls(cached);
            return cached;
        }

        User targetUser = userService.getById(targetUserId);
        if (targetUser == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }

        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(targetUser.getId());
        vo.setNickname(targetUser.getNickname());
        vo.setAvatar(ossUrlHelper.toPublicUrl(targetUser.getAvatar()));
        vo.setSignature(targetUser.getSignature());
        vo.setGender(targetUser.getGender());
        vo.setIsSelf(targetUserId.equals(currentUserId));
        vo.setIsFollow(checkIsFollow(currentUserId, targetUserId));
        vo.setStats(buildStats(targetUserId));
        vo.setMemes(queryLatestUserMemes(targetUserId, 3));
        vo.setFavorites(queryLatestUserFavorites(targetUserId, 3));

        writeCache(cacheKey, vo);
        return vo;
    }

    @Override
    public EditProfileEchoVO getEditProfileEcho(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }
        EditProfileEchoVO vo = new EditProfileEchoVO();
        vo.setAvatar(ossUrlHelper.toPublicUrl(user.getAvatar()));
        vo.setNickname(user.getNickname());
        vo.setSignature(user.getSignature());
        vo.setGender(user.getGender());
        vo.setBirthday(user.getBirthday() == null ? null : user.getBirthday().format(DateTimeFormatter.ISO_LOCAL_DATE));
        return vo;
    }

    @Override
    public UserMemePageVO pageUserMemes(Long targetUserId, Long currentUserId, Integer page, Integer size) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不合法");
        }
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        // 状态隔离：本人看所有状态，他人只看 status=1
        boolean isOwner = currentUserId != null && currentUserId.equals(targetUserId);

        Page<Meme> mpPage = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<Meme> wrapper = new LambdaQueryWrapper<Meme>()
                .eq(Meme::getUserId, targetUserId)
                .orderByDesc(Meme::getReleaseTime)
                .orderByDesc(Meme::getId);
        if (isOwner) {
            wrapper.ne(Meme::getStatus, 4);
        } else {
            wrapper.eq(Meme::getStatus, 1);
        }
        IPage<Meme> result = memeService.page(mpPage, wrapper);

        User targetUser = userService.getById(targetUserId);
        if (targetUser == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }
        AuthorVO author = authorSupport.toAuthorVO(targetUserId, targetUser, true);

        List<Meme> records = result.getRecords();
        List<UserPublishedMemeVO> list = records.isEmpty()
                ? Collections.emptyList()
                : buildPublishedMemeVOList(records, author);

        UserMemePageVO pageVO = new UserMemePageVO();
        pageVO.setAuthor(author);
        pageVO.setList(list);
        pageVO.setPage(pageNo);
        pageVO.setSize(pageSize);
        pageVO.setTotal(result.getTotal());
        pageVO.setOwner(isOwner);
        pageVO.setHasMore((long) pageNo * pageSize < result.getTotal());
        return pageVO;
    }

    /**
     * 批量组装发布梗 VO：标签通过一次 JOIN 查询按 meme_id 分组，内存组装，避免 N+1 与笛卡尔积。
     */
    private List<UserPublishedMemeVO> buildPublishedMemeVOList(List<Meme> memes, AuthorVO author) {
        List<Integer> memeIds = memes.stream().map(Meme::getId).toList();
        Map<Integer, List<MemeTagBindDTO>> tagMap = loadTagsByMemeIds(memeIds);

        List<UserPublishedMemeVO> list = new ArrayList<>(memes.size());
        for (Meme meme : memes) {
            UserPublishedMemeVO vo = new UserPublishedMemeVO();
            vo.setId(meme.getId());
            vo.setMemeId(meme.getId());
            vo.setName(meme.getName());
            vo.setIntroduction(meme.getIntroduction());
            vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
            vo.setPageViews(meme.getPageViews());
            vo.setLikes(meme.getLikes());
            vo.setComments(meme.getComments());
            vo.setStatus(meme.getStatus());
            vo.setStatusDesc(statusDesc(meme.getStatus()));
            vo.setTags(toUserMemeTagVOList(tagMap.get(meme.getId())));
            vo.setCreateTime(meme.getReleaseTime());
            vo.setReleaseTime(meme.getReleaseTime());
            vo.setAuthor(author);
            list.add(vo);
        }
        return list;
    }

    private Map<Integer, List<MemeTagBindDTO>> loadTagsByMemeIds(List<Integer> memeIds) {
        if (memeIds == null || memeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MemeTagBindDTO> rows = memeTagRelationMapper.selectTagsByMemeIds(memeIds);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<MemeTagBindDTO>> map = new HashMap<>();
        for (MemeTagBindDTO row : rows) {
            map.computeIfAbsent(row.getMemeId(), k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private List<UserMemeTagVO> toUserMemeTagVOList(List<MemeTagBindDTO> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserMemeTagVO> list = new ArrayList<>(tags.size());
        for (MemeTagBindDTO t : tags) {
            UserMemeTagVO vo = new UserMemeTagVO();
            vo.setTagId(t.getId());
            vo.setName(t.getName());
            list.add(vo);
        }
        return list;
    }

    private String statusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "正常";
            case 2 -> "审核中";
            case 3 -> "已下架";
            case 4 -> "已彻底删除";
            default -> "未知";
        };
    }

    @Override
    public void evictUserMemesCache(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        try {
            // 清理发布列表分页缓存与 ZSet 索引，保证下次查询拿到最新数据
            Set<String> keys = stringRedisTemplate.keys(MEMES_CACHE_PREFIX + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
            stringRedisTemplate.delete("user:memes:zset:" + userId);
        } catch (Exception ignored) {
            // 缓存清理失败不影响主流程
        }
    }

    @Override
    public PageVO<UserFavoriteItemVO> pageUserFavorites(Long targetUserId, Long currentUserId, Long folderId, Integer page, Integer size) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不合法");
        }
        boolean isOwner = currentUserId != null && currentUserId.equals(targetUserId);
        long resolvedFolderId = folderId == null ? IUserFavoriteFolderService.DEFAULT_FOLDER_ID : folderId;
        if (resolvedFolderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }

        // 权限隔离：他人不可查看私密默认夹，仅可查看公开夹
        if (!isOwner) {
            if (resolvedFolderId == IUserFavoriteFolderService.DEFAULT_FOLDER_ID) {
                if (!userFavoriteFolderService.isDefaultFolderPublic(targetUserId)) {
                    PageVO<UserFavoriteItemVO> empty = new PageVO<>();
                    empty.setList(Collections.emptyList());
                    empty.setPage(normalizePage(page));
                    empty.setSize(normalizeSize(size));
                    empty.setTotal(0L);
                    empty.setHasMore(false);
                    return empty;
                }
            } else {
                userFavoriteFolderService.assertFolderOwnedByUser(targetUserId, resolvedFolderId);
                boolean isPublic = isFolderPublic(targetUserId, resolvedFolderId);
                if (!isPublic) {
                    throw new BizException(Result.CODE_FORBIDDEN, "该收藏夹不可查看");
                }
            }
        }

        int pageNo = normalizePage(page);
        int pageSize = normalizeSize(size);
        String cacheKey = FAVORITES_CACHE_PREFIX + targetUserId + ":f" + resolvedFolderId + ":" + pageNo + ":" + pageSize + ":v2";
        PageVO<UserFavoriteItemVO> cached = readCache(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            refreshFavoriteImages(cached.getList());
            return cached;
        }

        long total = userFavoriteMapper.countVisibleFavoriteMemes(targetUserId, resolvedFolderId, isOwner);
        long offset = (long) (pageNo - 1) * pageSize;
        List<UserFavoriteMemeJoinRow> rows = userFavoriteMapper.selectVisibleFavoriteMemePage(
                targetUserId, resolvedFolderId, isOwner, offset, pageSize);

        List<UserFavoriteItemVO> list = new ArrayList<>();
        for (UserFavoriteMemeJoinRow row : rows) {
            if (row == null || row.getMemeId() == null) {
                continue;
            }
            UserFavoriteItemVO item = new UserFavoriteItemVO();
            item.setId(row.getMemeId());
            item.setFavoriteId(row.getFavoriteId());
            item.setName(row.getName());
            item.setImage(ossUrlHelper.toPublicUrl(row.getImage()));
            item.setPageViews(row.getPageViews());
            item.setFolderId(row.getFolderId());
            item.setSortOrder(row.getSortOrder());
            item.setFavoriteTime(row.getFavoriteTime());
            list.add(item);
        }

        PageVO<UserFavoriteItemVO> pageVO = new PageVO<>();
        pageVO.setList(list);
        pageVO.setPage(pageNo);
        pageVO.setSize(pageSize);
        pageVO.setTotal(total);
        pageVO.setHasMore(offset + list.size() < total);

        writeCache(cacheKey, pageVO);
        return pageVO;
    }

    private boolean isFolderPublic(Long targetUserId, Long folderId) {
        com.sakana.just_because_meme_understands_you.entity.UserFavoriteFolder folder =
                userFavoriteFolderMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sakana.just_because_meme_understands_you.entity.UserFavoriteFolder>()
                                .eq(com.sakana.just_because_meme_understands_you.entity.UserFavoriteFolder::getId, folderId)
                                .eq(com.sakana.just_because_meme_understands_you.entity.UserFavoriteFolder::getUserId, targetUserId)
                                .eq(com.sakana.just_because_meme_understands_you.entity.UserFavoriteFolder::getIsDeleted, 0)
                                .last("LIMIT 1"));
        return folder != null && folder.getIsPublic() != null && folder.getIsPublic() == 1;
    }

    @Override
    public UploadAvatarVO uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BizException(Result.CODE_BAD_REQUEST, "头像文件不能超过 5MB");
        }
        if (!StringUtils.hasText(bucketName)) {
            throw new BizException(Result.CODE_ERROR, "未配置 OSS bucketName");
        }
        String suffix = getFileSuffix(file.getOriginalFilename());
        if (!isAllowedImageSuffix(suffix)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "仅支持 jpg/png/webp 图片");
        }

        String objectKey = "avatar/" + userId + "/" + UUID.randomUUID() + "." + suffix;
        try {
            ossClient.putObject(bucketName, objectKey, file.getInputStream());
        } catch (IOException ignored) {
            throw new BizException(Result.CODE_ERROR, "头像上传失败，请稍后重试");
        }

        UploadAvatarVO vo = new UploadAvatarVO();
        vo.setUrl(ossUrlHelper.toPublicUrl(objectKey));
        return vo;
    }

    @Override
    public AccountProfileVO updateProfile(Long userId, UserProfileUpdateRequestDTO request) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求参数不能为空");
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }

        if (request.getNickname() != null) {
            String nickname = request.getNickname().trim();
            if (nickname.length() > 50) {
                throw new BizException(Result.CODE_BAD_REQUEST, "昵称长度最多 50 个字符");
            }
            if (StringUtils.hasText(nickname)) {
                user.setNickname(nickname);
            }
        }
        if (request.getGender() != null) {
            if (request.getGender() < 0 || request.getGender() > 2) {
                throw new BizException(Result.CODE_BAD_REQUEST, "gender 参数不合法");
            }
            user.setGender(request.getGender());
        }
        if (request.getBirthday() != null) {
            String birthdayRaw = request.getBirthday().trim();
            if (!StringUtils.hasText(birthdayRaw)) {
                user.setBirthday(null);
            } else {
                try {
                    user.setBirthday(LocalDate.parse(birthdayRaw));
                } catch (Exception ignored) {
                    throw new BizException(Result.CODE_BAD_REQUEST, "birthday 格式应为 yyyy-MM-dd");
                }
            }
        }
        if (request.getSignature() != null) {
            String signature = request.getSignature().trim();
            if (signature.length() > 255) {
                throw new BizException(Result.CODE_BAD_REQUEST, "签名最多 255 个字符");
            }
            user.setSignature(StringUtils.hasText(signature) ? signature : null);
        }
        if (request.getAvatar() != null) {
            String avatarRaw = request.getAvatar().trim();
            if (!StringUtils.hasText(avatarRaw)) {
                user.setAvatar(null);
            } else {
                String avatarKey = ossUrlHelper.normalizeForStorage(avatarRaw);
                ossUrlHelper.assertOwnedImageKey(avatarKey, "avatar/" + userId + "/");
                user.setAvatar(avatarKey);
            }
        }

        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);
        clearUserCache(userId);

        AccountProfileVO vo = new AccountProfileVO();
        vo.setUserId(String.valueOf(user.getId()));
        vo.setNickname(user.getNickname() == null ? "" : user.getNickname().trim());
        vo.setAvatar(ossUrlHelper.toPublicUrl(user.getAvatar()));
        if (vo.getAvatar() == null) {
            vo.setAvatar("");
        }
        vo.setSignature(user.getSignature() == null ? "" : user.getSignature().trim());
        vo.setGender(user.getGender() == null ? 0 : user.getGender());
        vo.setBirthday(user.getBirthday() == null ? "" : user.getBirthday().toString());
        return vo;
    }

    @Override
    public void evictUserCache(Long userId) {
        clearUserCache(userId);
    }

    private UserProfileStatsVO buildStats(Long userId) {
        UserStats stats = userStatsMapper.selectById(userId);
        UserProfileStatsVO vo = new UserProfileStatsVO();
        if (stats == null) {
            vo.setFollowCount(0);
            vo.setFansCount(0);
            vo.setMemeCount(0);
            vo.setLikeReceived(0);
            vo.setFavoriteCount(userFavoriteCountService.getFavoriteCount(userId));
            return vo;
        }
        vo.setFollowCount(defaultInt(stats.getFollowCount()));
        vo.setFansCount(defaultInt(stats.getFansCount()));
        vo.setMemeCount(defaultInt(stats.getMemeCount()));
        vo.setLikeReceived(defaultInt(stats.getLikeReceived()));
        vo.setFavoriteCount(userFavoriteCountService.getFavoriteCount(userId));
        return vo;
    }

    private Boolean checkIsFollow(Long currentUserId, Long targetUserId) {
        if (currentUserId == null || currentUserId <= 0 || currentUserId.equals(targetUserId)) {
            return false;
        }
        LambdaQueryWrapper<UserRelation> wrapper = new LambdaQueryWrapper<UserRelation>()
                .eq(UserRelation::getFromUserId, currentUserId)
                .eq(UserRelation::getToUserId, targetUserId)
                .last("LIMIT 1");
        return userRelationMapper.selectCount(wrapper) > 0;
    }

    private List<UserMemeItemVO> queryLatestUserMemes(Long userId, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Meme> wrapper = new LambdaQueryWrapper<Meme>()
                .eq(Meme::getUserId, userId)
                .eq(Meme::getStatus, 1)
                .orderByDesc(Meme::getReleaseTime)
                .last("LIMIT " + limit);
        List<Meme> memes = memeService.list(wrapper);
        if (memes == null || memes.isEmpty()) {
            return Collections.emptyList();
        }
        return memes.stream().map(this::toUserMemeItem).toList();
    }

    private List<UserFavoriteItemVO> queryLatestUserFavorites(Long userId, int limit) {
        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getIsDeleted, FAVORITE_NOT_DELETED)
                .orderByDesc(UserFavorite::getCreateTime)
                .last("LIMIT " + limit);
        List<UserFavorite> favorites = userFavoriteMapper.selectList(wrapper);
        if (favorites == null || favorites.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> memeIds = favorites.stream().map(UserFavorite::getMemeId).toList();
        Map<Long, Meme> memeMap = queryMemeMap(memeIds);
        List<UserFavoriteItemVO> list = new ArrayList<>();
        for (UserFavorite favorite : favorites) {
            Meme meme = memeMap.get(favorite.getMemeId());
            if (meme == null) {
                continue;
            }
            UserFavoriteItemVO vo = new UserFavoriteItemVO();
            vo.setId(meme.getId() == null ? null : meme.getId().longValue());
            vo.setName(meme.getName());
            vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
            vo.setPageViews(meme.getPageViews());
            list.add(vo);
        }
        return list;
    }

    private Map<Long, Meme> queryMemeMap(List<Long> memeIds) {
        if (memeIds == null || memeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Integer> ids = memeIds.stream().map(Long::intValue).toList();
        LambdaQueryWrapper<Meme> wrapper = new LambdaQueryWrapper<Meme>()
                .in(Meme::getId, ids)
                .eq(Meme::getStatus, 1);
        List<Meme> memes = memeService.list(wrapper);
        if (memes == null || memes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Meme> memeMap = new HashMap<>();
        for (Meme meme : memes) {
            if (meme.getId() != null) {
                memeMap.put(meme.getId().longValue(), meme);
            }
        }
        return memeMap;
    }

    private UserMemeItemVO toUserMemeItem(Meme meme) {
        UserMemeItemVO vo = new UserMemeItemVO();
        vo.setId(meme.getId() == null ? null : meme.getId().longValue());
        vo.setName(meme.getName());
        vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
        vo.setPageViews(meme.getPageViews());
        vo.setLikes(meme.getLikes());
        vo.setComments(meme.getComments());
        vo.setReleaseTime(meme.getReleaseTime());
        return vo;
    }

    private void refreshFavoriteImages(List<UserFavoriteItemVO> list) {
        if (list == null) {
            return;
        }
        for (UserFavoriteItemVO item : list) {
            if (item != null) {
                item.setImage(ossUrlHelper.toPublicUrl(item.getImage()));
            }
        }
    }

    private int normalizePage(Integer page) {
        return PageParamNormalizer.normalizePage(page);
    }

    private int normalizeSize(Integer size) {
        return PageParamNormalizer.normalizeSize(size);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String getFileSuffix(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        String suffix = filename.substring(filename.lastIndexOf('.') + 1);
        return suffix.toLowerCase();
    }

    private boolean isAllowedImageSuffix(String suffix) {
        return "jpg".equals(suffix)
                || "jpeg".equals(suffix)
                || "png".equals(suffix)
                || "webp".equals(suffix);
    }

    private <T> T readCache(String key, TypeReference<T> typeReference) {
        try {
            String cacheKey = Objects.requireNonNull(key, "cache key");
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
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
            String cacheKey = Objects.requireNonNull(key, "cache key");
            String json = objectMapper.writeValueAsString(value);
            if (!StringUtils.hasText(json)) {
                return;
            }
            String cacheValue = Objects.requireNonNull(json, "cache value");
            stringRedisTemplate.opsForValue().set(cacheKey, cacheValue, CACHE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception ignored) {
            // ignore cache write failure
        }
    }

    private void clearUserCache(Long userId) {
        Set<String> profileKeys = stringRedisTemplate.keys(PROFILE_CACHE_PREFIX + userId + ":*");
        Set<String> memeKeys = stringRedisTemplate.keys(MEMES_CACHE_PREFIX + userId + ":*");
        Set<String> favoriteKeys = stringRedisTemplate.keys(FAVORITES_CACHE_PREFIX + userId + ":*");
        if (profileKeys != null && !profileKeys.isEmpty()) {
            stringRedisTemplate.delete(profileKeys);
        }
        if (memeKeys != null && !memeKeys.isEmpty()) {
            stringRedisTemplate.delete(memeKeys);
        }
        if (favoriteKeys != null && !favoriteKeys.isEmpty()) {
            stringRedisTemplate.delete(favoriteKeys);
        }
    }
}
