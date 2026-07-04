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
import com.sakana.just_because_meme_understands_you.dto.UserProfileUpdateRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.entity.UserRelation;
import com.sakana.just_because_meme_understands_you.entity.UserStats;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserRelationMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserStatsMapper;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.service.user.IUserProfileService;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import com.sakana.just_because_meme_understands_you.service.user.UserFavoriteCountService;
import com.sakana.just_because_meme_understands_you.vo.EditProfileEchoVO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.UploadAvatarVO;
import com.sakana.just_because_meme_understands_you.vo.UserFavoriteItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemeItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserProfileStatsVO;
import com.sakana.just_because_meme_understands_you.vo.UserProfileVO;
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
    private IMemeService memeService;

    @Resource
    private UserStatsMapper userStatsMapper;

    @Resource
    private UserFavoriteMapper userFavoriteMapper;

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

    @Value("${oss.bucketName}")
    private String bucketName;

    @Value("${oss.endpoint}")
    private String ossEndpoint;

    @Override
    public UserProfileVO getUserProfile(Long targetUserId, Long currentUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不合法");
        }
        String cacheKey = PROFILE_CACHE_PREFIX + targetUserId + ":" + currentUserId;
        UserProfileVO cached = readCache(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            return cached;
        }

        User targetUser = userService.getById(targetUserId);
        if (targetUser == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }

        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(targetUser.getId());
        vo.setNickname(targetUser.getNickname());
        vo.setAvatar(targetUser.getAvatar());
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
        vo.setAvatar(user.getAvatar());
        vo.setNickname(user.getNickname());
        vo.setSignature(user.getSignature());
        vo.setGender(user.getGender());
        vo.setBirthday(user.getBirthday() == null ? null : user.getBirthday().format(DateTimeFormatter.ISO_LOCAL_DATE));
        return vo;
    }

    @Override
    public PageVO<UserMemeItemVO> pageUserMemes(Long userId, Integer page, Integer size) {
        int pageNo = normalizePage(page);
        int pageSize = normalizeSize(size);
        String cacheKey = MEMES_CACHE_PREFIX + userId + ":" + pageNo + ":" + pageSize;
        PageVO<UserMemeItemVO> cached = readCache(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            return cached;
        }

        Page<Meme> mpPage = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<Meme> wrapper = new LambdaQueryWrapper<Meme>()
                .eq(Meme::getUserId, userId)
                .eq(Meme::getStatus, 1)
                .orderByDesc(Meme::getReleaseTime);
        IPage<Meme> result = memeService.page(mpPage, wrapper);

        List<UserMemeItemVO> list = result.getRecords().stream().map(this::toUserMemeItem).toList();
        PageVO<UserMemeItemVO> pageVO = new PageVO<>();
        pageVO.setList(list);
        pageVO.setPage(pageNo);
        pageVO.setSize(pageSize);
        pageVO.setTotal(result.getTotal());

        writeCache(cacheKey, pageVO);
        return pageVO;
    }

    @Override
    public PageVO<UserFavoriteItemVO> pageUserFavorites(Long userId, Integer page, Integer size) {
        int pageNo = normalizePage(page);
        int pageSize = normalizeSize(size);
        String cacheKey = FAVORITES_CACHE_PREFIX + userId + ":" + pageNo + ":" + pageSize;
        PageVO<UserFavoriteItemVO> cached = readCache(cacheKey, new TypeReference<>() {});
        if (cached != null) {
            return cached;
        }

        Page<UserFavorite> favoritePage = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<UserFavorite> favoriteWrapper = new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getIsDeleted, FAVORITE_NOT_DELETED)
                .orderByDesc(UserFavorite::getCreateTime);
        IPage<UserFavorite> result = userFavoriteMapper.selectPage(favoritePage, favoriteWrapper);
        List<UserFavorite> records = result.getRecords();

        List<Long> memeIdList = records.stream().map(UserFavorite::getMemeId).toList();
        Map<Long, Meme> memeMap = queryMemeMap(memeIdList);
        List<UserFavoriteItemVO> list = new ArrayList<>();
        for (UserFavorite favorite : records) {
            Meme meme = memeMap.get(favorite.getMemeId());
            if (meme == null) {
                continue;
            }
            UserFavoriteItemVO item = new UserFavoriteItemVO();
            item.setId(meme.getId() == null ? null : meme.getId().longValue());
            item.setName(meme.getName());
            item.setImage(meme.getImage());
            item.setPageViews(meme.getPageViews());
            list.add(item);
        }

        PageVO<UserFavoriteItemVO> pageVO = new PageVO<>();
        pageVO.setList(list);
        pageVO.setPage(pageNo);
        pageVO.setSize(pageSize);
        pageVO.setTotal(result.getTotal());

        writeCache(cacheKey, pageVO);
        return pageVO;
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
        vo.setUrl("https://" + bucketName + "." + ossEndpoint + "/" + objectKey);
        return vo;
    }

    @Override
    public void updateProfile(Long userId, UserProfileUpdateRequestDTO request) {
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getNickname())
                || request.getGender() == null
                || !StringUtils.hasText(request.getBirthday())
                || !StringUtils.hasText(request.getSignature())
                || !StringUtils.hasText(request.getAvatar())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "资料参数不完整");
        }
        if (request.getGender() < 0 || request.getGender() > 2) {
            throw new BizException(Result.CODE_BAD_REQUEST, "gender 参数不合法");
        }

        LocalDate birthday;
        try {
            birthday = LocalDate.parse(request.getBirthday());
        } catch (Exception ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, "birthday 格式应为 yyyy-MM-dd");
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }
        user.setNickname(request.getNickname().trim());
        user.setGender(request.getGender());
        user.setBirthday(birthday);
        user.setSignature(request.getSignature().trim());
        user.setAvatar(request.getAvatar().trim());
        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);

        clearUserCache(userId);
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
            vo.setImage(meme.getImage());
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
        vo.setImage(meme.getImage());
        vo.setPageViews(meme.getPageViews());
        vo.setLikes(meme.getLikes());
        vo.setComments(meme.getComments());
        vo.setReleaseTime(meme.getReleaseTime());
        return vo;
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
