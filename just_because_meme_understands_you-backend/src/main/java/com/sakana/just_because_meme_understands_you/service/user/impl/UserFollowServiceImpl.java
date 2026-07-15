package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.entity.UserRelation;
import com.sakana.just_because_meme_understands_you.entity.UserStats;
import com.sakana.just_because_meme_understands_you.mapper.UserRelationMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserStatsMapper;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.service.user.FollowRateLimiter;
import com.sakana.just_because_meme_understands_you.service.user.IUserFollowService;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import com.sakana.just_because_meme_understands_you.vo.FollowBatchItemVO;
import com.sakana.just_because_meme_understands_you.vo.FollowBatchStatusVO;
import com.sakana.just_because_meme_understands_you.vo.FollowListItemVO;
import com.sakana.just_because_meme_understands_you.vo.FollowListPageVO;
import com.sakana.just_because_meme_understands_you.vo.FollowedVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserFollowServiceImpl implements IUserFollowService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int USER_ACTIVE = 1;
    private static final int MAX_BATCH_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final DateTimeFormatter FOLLOW_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private UserRelationMapper userRelationMapper;

    @Resource
    private UserStatsMapper userStatsMapper;

    @Resource
    private IUserService userService;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private FollowRateLimiter followRateLimiter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowedVO follow(Long currentUserId, Long targetUserId) {
        requireUserId(currentUserId);
        requireUserId(targetUserId);
        if (currentUserId.equals(targetUserId)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能关注自己");
        }
        followRateLimiter.check(currentUserId);
        User target = requireActiveUser(targetUserId);

        UserRelation active = findRelation(currentUserId, targetUserId, NOT_DELETED);
        if (active != null) {
            return buildFollowedVO(targetUserId, true, currentUserId);
        }

        LocalDateTime now = LocalDateTime.now();
        UserRelation deleted = findRelation(currentUserId, targetUserId, DELETED);
        if (deleted != null) {
            int updated = userRelationMapper.update(null, new LambdaUpdateWrapper<UserRelation>()
                    .eq(UserRelation::getId, deleted.getId())
                    .eq(UserRelation::getIsDeleted, DELETED)
                    .set(UserRelation::getIsDeleted, NOT_DELETED)
                    .set(UserRelation::getUpdateTime, now)
                    .set(UserRelation::getCreateTime, now));
            if (updated > 0) {
                applyFollowDelta(currentUserId, targetUserId, 1);
                return buildFollowedVO(target.getId(), true, currentUserId);
            }
            if (findRelation(currentUserId, targetUserId, NOT_DELETED) != null) {
                return buildFollowedVO(target.getId(), true, currentUserId);
            }
        }

        UserRelation relation = new UserRelation();
        relation.setFromUserId(currentUserId);
        relation.setToUserId(targetUserId);
        relation.setIsDeleted(NOT_DELETED);
        relation.setCreateTime(now);
        relation.setUpdateTime(now);
        try {
            userRelationMapper.insert(relation);
        } catch (DuplicateKeyException e) {
            log.debug("关注并发冲突, from={}, to={}", currentUserId, targetUserId);
            if (findRelation(currentUserId, targetUserId, NOT_DELETED) != null) {
                return buildFollowedVO(target.getId(), true, currentUserId);
            }
            throw new BizException(Result.CODE_ERROR, "关注失败，请稍后重试");
        }

        applyFollowDelta(currentUserId, targetUserId, 1);
        return buildFollowedVO(target.getId(), true, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowedVO unfollow(Long currentUserId, Long targetUserId) {
        requireUserId(currentUserId);
        requireUserId(targetUserId);
        if (currentUserId.equals(targetUserId)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能取消关注自己");
        }
        followRateLimiter.check(currentUserId);
        requireActiveUser(targetUserId);

        UserRelation active = findRelation(currentUserId, targetUserId, NOT_DELETED);
        if (active == null) {
            return buildFollowedVO(targetUserId, false, currentUserId);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = userRelationMapper.update(null, new LambdaUpdateWrapper<UserRelation>()
                .eq(UserRelation::getId, active.getId())
                .eq(UserRelation::getIsDeleted, NOT_DELETED)
                .set(UserRelation::getIsDeleted, DELETED)
                .set(UserRelation::getUpdateTime, now));
        if (updated > 0) {
            applyFollowDelta(currentUserId, targetUserId, -1);
        }
        return buildFollowedVO(targetUserId, false, currentUserId);
    }

    @Override
    public FollowedVO getFollowStatus(Long currentUserId, Long targetUserId) {
        requireUserId(currentUserId);
        requireUserId(targetUserId);
        requireActiveUser(targetUserId);
        boolean followed = !currentUserId.equals(targetUserId)
                && findRelation(currentUserId, targetUserId, NOT_DELETED) != null;
        return buildFollowedVO(targetUserId, followed, currentUserId);
    }

    @Override
    public FollowBatchStatusVO batchFollowStatus(Long currentUserId, String userIdsCsv) {
        requireUserId(currentUserId);
        List<Long> targetIds = parseUserIdsCsv(userIdsCsv);
        FollowBatchStatusVO vo = new FollowBatchStatusVO();
        if (targetIds.isEmpty()) {
            vo.setItems(Collections.emptyList());
            return vo;
        }

        Set<Long> followedIds = listFollowedTargetIds(currentUserId, targetIds);
        List<FollowBatchItemVO> items = new ArrayList<>(targetIds.size());
        for (Long id : targetIds) {
            FollowBatchItemVO item = new FollowBatchItemVO();
            item.setUserId(String.valueOf(id));
            item.setFollowed(!currentUserId.equals(id) && followedIds.contains(id));
            items.add(item);
        }
        vo.setItems(items);
        return vo;
    }

    @Override
    public FollowListPageVO pageFollowing(Long targetUserId, Long currentUserId, Integer page, Integer size) {
        return pageRelationList(targetUserId, currentUserId, page, size, true);
    }

    @Override
    public FollowListPageVO pageFollowers(Long targetUserId, Long currentUserId, Integer page, Integer size) {
        return pageRelationList(targetUserId, currentUserId, page, size, false);
    }

    /**
     * @param following true=关注列表（from=target），false=粉丝列表（to=target）
     */
    private FollowListPageVO pageRelationList(Long targetUserId, Long currentUserId,
                                             Integer page, Integer size, boolean following) {
        requireUserId(targetUserId);
        requireActiveUser(targetUserId);

        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, PageParamNormalizer.MAX_SIZE);

        Page<UserRelation> mpPage = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<UserRelation> wrapper = new LambdaQueryWrapper<UserRelation>()
                .eq(following ? UserRelation::getFromUserId : UserRelation::getToUserId, targetUserId)
                .eq(UserRelation::getIsDeleted, NOT_DELETED)
                .orderByDesc(UserRelation::getCreateTime)
                .orderByDesc(UserRelation::getId);
        Page<UserRelation> result = userRelationMapper.selectPage(mpPage, wrapper);
        List<UserRelation> records = result.getRecords() == null ? Collections.emptyList() : result.getRecords();

        List<Long> relatedUserIds = records.stream()
                .map(r -> following ? r.getToUserId() : r.getFromUserId())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = loadUserMap(relatedUserIds);

        Set<Long> followedByMe = currentUserId == null
                ? Collections.emptySet()
                : listFollowedTargetIds(currentUserId, relatedUserIds);
        Set<Long> followsMeBack = currentUserId == null
                ? Collections.emptySet()
                : listFollowedTargetIdsFromTargets(relatedUserIds, currentUserId);

        List<FollowListItemVO> list = new ArrayList<>(records.size());
        for (UserRelation relation : records) {
            Long uid = following ? relation.getToUserId() : relation.getFromUserId();
            if (uid == null) {
                continue;
            }
            User user = userMap.get(uid);
            FollowListItemVO item = new FollowListItemVO();
            item.setUserId(String.valueOf(uid));
            item.setNickname(user != null && StringUtils.hasText(user.getNickname())
                    ? user.getNickname().trim()
                    : "匿名用户");
            item.setAvatar(user != null ? ossUrlHelper.toPublicUrl(user.getAvatar()) : "");
            item.setSignature(user != null && StringUtils.hasText(user.getSignature())
                    ? user.getSignature().trim()
                    : "");
            item.setFollowTime(relation.getCreateTime() == null
                    ? ""
                    : relation.getCreateTime().format(FOLLOW_TIME_FMT));
            boolean byMe = currentUserId != null && followedByMe.contains(uid);
            item.setFollowedByMe(byMe);
            item.setMutual(byMe && followsMeBack.contains(uid));
            list.add(item);
        }

        FollowListPageVO pageVO = new FollowListPageVO();
        pageVO.setList(list);
        pageVO.setPage(pageNo);
        pageVO.setSize(pageSize);
        pageVO.setTotal(result.getTotal());
        pageVO.setHasMore((long) pageNo * pageSize < result.getTotal());
        return pageVO;
    }

    private FollowedVO buildFollowedVO(Long targetUserId, boolean followed, Long currentUserId) {
        UserStats stats = ensureStats(targetUserId);
        boolean mutual = followed
                && currentUserId != null
                && !currentUserId.equals(targetUserId)
                && findRelation(targetUserId, currentUserId, NOT_DELETED) != null;

        FollowedVO vo = new FollowedVO();
        vo.setUserId(String.valueOf(targetUserId));
        vo.setFollowed(followed);
        vo.setFollowCount(defaultInt(stats.getFollowCount()));
        vo.setFansCount(defaultInt(stats.getFansCount()));
        vo.setMutual(mutual);
        return vo;
    }

    private void applyFollowDelta(Long fromUserId, Long toUserId, int delta) {
        ensureStats(fromUserId);
        ensureStats(toUserId);
        if (delta > 0) {
            userStatsMapper.incrementFollowCount(fromUserId);
            userStatsMapper.incrementFansCount(toUserId);
        } else if (delta < 0) {
            userStatsMapper.decrementFollowCount(fromUserId);
            userStatsMapper.decrementFansCount(toUserId);
        }
    }

    private UserStats ensureStats(Long userId) {
        UserStats stats = userStatsMapper.selectById(userId);
        if (stats != null) {
            return stats;
        }
        UserStats created = new UserStats();
        created.setUserId(userId);
        created.setMemeCount(0);
        created.setLikeReceived(0);
        created.setFavoriteCount(0);
        created.setFollowCount(0);
        created.setFansCount(0);
        try {
            userStatsMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ignored) {
            UserStats again = userStatsMapper.selectById(userId);
            if (again != null) {
                return again;
            }
            throw new BizException(Result.CODE_ERROR, "用户统计初始化失败");
        }
    }

    private UserRelation findRelation(Long fromUserId, Long toUserId, int deletedFlag) {
        return userRelationMapper.selectOne(new LambdaQueryWrapper<UserRelation>()
                .eq(UserRelation::getFromUserId, fromUserId)
                .eq(UserRelation::getToUserId, toUserId)
                .eq(UserRelation::getIsDeleted, deletedFlag)
                .last("LIMIT 1"));
    }

    private Set<Long> listFollowedTargetIds(Long fromUserId, List<Long> toUserIds) {
        if (fromUserId == null || toUserIds == null || toUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<UserRelation> relations = userRelationMapper.selectList(new LambdaQueryWrapper<UserRelation>()
                .eq(UserRelation::getFromUserId, fromUserId)
                .in(UserRelation::getToUserId, toUserIds)
                .eq(UserRelation::getIsDeleted, NOT_DELETED)
                .select(UserRelation::getToUserId));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptySet();
        }
        return relations.stream()
                .map(UserRelation::getToUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** 查询 targets 中谁关注了 currentUser（用于互关判定）。 */
    private Set<Long> listFollowedTargetIdsFromTargets(List<Long> fromUserIds, Long toUserId) {
        if (toUserId == null || fromUserIds == null || fromUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<UserRelation> relations = userRelationMapper.selectList(new LambdaQueryWrapper<UserRelation>()
                .in(UserRelation::getFromUserId, fromUserIds)
                .eq(UserRelation::getToUserId, toUserId)
                .eq(UserRelation::getIsDeleted, NOT_DELETED)
                .select(UserRelation::getFromUserId));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptySet();
        }
        return relations.stream()
                .map(UserRelation::getFromUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Map<Long, User> loadUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userService.listByIds(userIds);
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new HashMap<>(users.size());
        for (User user : users) {
            if (user != null && user.getId() != null) {
                map.put(user.getId(), user);
            }
        }
        return map;
    }

    private User requireActiveUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != USER_ACTIVE) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不合法");
        }
    }

    private List<Long> parseUserIdsCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Collections.emptyList();
        }
        String[] parts = csv.split(",");
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // skip invalid
            }
            if (ids.size() >= MAX_BATCH_SIZE) {
                break;
            }
        }
        return new ArrayList<>(ids);
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }
}
