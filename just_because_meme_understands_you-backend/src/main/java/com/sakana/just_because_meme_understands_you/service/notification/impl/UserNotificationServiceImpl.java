package com.sakana.just_because_meme_understands_you.service.notification.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.DataStatusConstants;
import com.sakana.just_because_meme_understands_you.common.constant.NotificationConstants;
import com.sakana.just_because_meme_understands_you.common.support.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.dto.NotificationBatchDeleteRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.entity.UserNotification;
import com.sakana.just_because_meme_understands_you.mapper.UserNotificationMapper;
import com.sakana.just_because_meme_understands_you.service.notification.IUserNotificationService;
import com.sakana.just_because_meme_understands_you.service.notification.assembler.NotificationAssembler;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import com.sakana.just_because_meme_understands_you.vo.NotificationBatchDeleteVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationDeleteVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationPageVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationReadAllVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationReadVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationUnreadCountVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserNotificationServiceImpl implements IUserNotificationService {

    private static final int READ = 1;
    private static final int UNREAD = 0;
    private static final int MAX_BATCH_DELETE = 100;

    @Resource
    private UserNotificationMapper userNotificationMapper;

    @Resource
    private IUserService userService;

    @Resource
    private NotificationAssembler notificationAssembler;

    @Override
    public NotificationPageVO pageNotifications(Long userId, Integer page, Integer size, String type, String tab) {
        requireUserId(userId);
        validateTab(tab);

        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<UserNotification> wrapper = baseVisibleWrapper(userId);
        applyListFilters(wrapper, type, tab);
        wrapper.orderByDesc(UserNotification::getCreateTime, UserNotification::getId);

        Page<UserNotification> pageResult = userNotificationMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<UserNotification> rows = pageResult.getRecords() != null ? pageResult.getRecords() : Collections.emptyList();

        NotificationPageVO vo = new NotificationPageVO();
        vo.setList(notificationAssembler.toItemList(rows, loadActorMap(rows)));
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(pageResult.getTotal());
        vo.setHasMore((long) pageNo * pageSize < pageResult.getTotal());
        return vo;
    }

    @Override
    public NotificationUnreadCountVO getUnreadCount(Long userId) {
        requireUserId(userId);
        NotificationUnreadCountVO vo = new NotificationUnreadCountVO();
        vo.setTotal(countUnread(userId, Collections.emptySet(), false));
        vo.setInteract(countUnread(userId, NotificationConstants.INTERACT_TYPES, true));
        vo.setSystem(countUnread(userId, NotificationConstants.SYSTEM_TYPES, true));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationReadVO markRead(Long userId, Long notificationId) {
        requireUserId(userId);
        requireNotificationId(notificationId);

        UserNotification row = requireOwnedNotification(userId, notificationId);
        if (row.getIsRead() == null || row.getIsRead() != READ) {
            LocalDateTime now = LocalDateTime.now();
            userNotificationMapper.update(null, new LambdaUpdateWrapper<UserNotification>()
                    .eq(UserNotification::getId, notificationId)
                    .eq(UserNotification::getUserId, userId)
                    .eq(UserNotification::getIsDeleted, DataStatusConstants.NOT_DELETED)
                    .set(UserNotification::getIsRead, READ)
                    .set(UserNotification::getUpdateTime, now));
        }

        NotificationReadVO vo = new NotificationReadVO();
        vo.setId(notificationId);
        vo.setIsRead(true);
        vo.setUnreadCount(countUnread(userId, Collections.emptySet(), false));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationReadAllVO markReadAll(Long userId, String tab) {
        requireUserId(userId);
        String normalizedTab = normalizeReadAllTab(tab);

        LambdaUpdateWrapper<UserNotification> update = new LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsDeleted, DataStatusConstants.NOT_DELETED)
                .eq(UserNotification::getIsRead, UNREAD)
                .set(UserNotification::getIsRead, READ)
                .set(UserNotification::getUpdateTime, LocalDateTime.now());

        if (NotificationConstants.TAB_INTERACT.equals(normalizedTab)) {
            update.in(UserNotification::getType, NotificationConstants.INTERACT_TYPES);
        } else if (NotificationConstants.TAB_SYSTEM.equals(normalizedTab)) {
            update.in(UserNotification::getType, NotificationConstants.SYSTEM_TYPES);
        }

        int updatedCount = userNotificationMapper.update(null, update);

        NotificationReadAllVO vo = new NotificationReadAllVO();
        vo.setUpdatedCount(updatedCount);
        vo.setUnreadCount(toIntCount(countUnread(userId, Collections.emptySet(), false)));
        vo.setInteract(toIntCount(countUnread(userId, NotificationConstants.INTERACT_TYPES, true)));
        vo.setSystem(toIntCount(countUnread(userId, NotificationConstants.SYSTEM_TYPES, true)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationDeleteVO deleteOne(Long userId, Long notificationId) {
        requireUserId(userId);
        requireNotificationId(notificationId);
        requireOwnedNotification(userId, notificationId);

        LocalDateTime now = LocalDateTime.now();
        int updated = userNotificationMapper.update(null, new LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getId, notificationId)
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsDeleted, DataStatusConstants.NOT_DELETED)
                .set(UserNotification::getIsDeleted, DataStatusConstants.DELETED)
                .set(UserNotification::getUpdateTime, now));
        if (updated <= 0) {
            throw new BizException(Result.CODE_NOT_FOUND, "消息不存在或已删除");
        }

        NotificationDeleteVO vo = new NotificationDeleteVO();
        vo.setId(notificationId);
        vo.setDeleted(true);
        vo.setUnreadCount(countUnread(userId, Collections.emptySet(), false));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationBatchDeleteVO batchDelete(Long userId, NotificationBatchDeleteRequestDTO request) {
        requireUserId(userId);
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求体不能为空");
        }

        boolean clearAll = Boolean.TRUE.equals(request.getClearAll());
        boolean clearReadOnly = Boolean.TRUE.equals(request.getClearReadOnly());
        List<String> rawIds = request.getIds() != null ? request.getIds() : Collections.emptyList();

        if (clearAll && clearReadOnly) {
            throw new BizException(Result.CODE_BAD_REQUEST, "clearAll 与 clearReadOnly 不能同时为 true");
        }
        if (!clearAll && !clearReadOnly && rawIds.isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请指定 ids，或设置 clearAll / clearReadOnly");
        }
        if (!rawIds.isEmpty() && (clearAll || clearReadOnly)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "ids 与 clearAll/clearReadOnly 不能同时使用");
        }

        LocalDateTime now = LocalDateTime.now();
        int deletedCount;

        if (clearAll) {
            deletedCount = userNotificationMapper.update(null, new LambdaUpdateWrapper<UserNotification>()
                    .eq(UserNotification::getUserId, userId)
                    .eq(UserNotification::getIsDeleted, DataStatusConstants.NOT_DELETED)
                    .set(UserNotification::getIsDeleted, DataStatusConstants.DELETED)
                    .set(UserNotification::getUpdateTime, now));
        } else if (clearReadOnly) {
            deletedCount = userNotificationMapper.update(null, new LambdaUpdateWrapper<UserNotification>()
                    .eq(UserNotification::getUserId, userId)
                    .eq(UserNotification::getIsDeleted, DataStatusConstants.NOT_DELETED)
                    .eq(UserNotification::getIsRead, READ)
                    .set(UserNotification::getIsDeleted, DataStatusConstants.DELETED)
                    .set(UserNotification::getUpdateTime, now));
        } else {
            List<Long> ids = parseIds(rawIds);
            if (ids.isEmpty()) {
                throw new BizException(Result.CODE_BAD_REQUEST, "ids 不能为空");
            }
            if (ids.size() > MAX_BATCH_DELETE) {
                throw new BizException(Result.CODE_BAD_REQUEST, "单次最多删除 " + MAX_BATCH_DELETE + " 条消息");
            }
            deletedCount = userNotificationMapper.update(null, new LambdaUpdateWrapper<UserNotification>()
                    .eq(UserNotification::getUserId, userId)
                    .eq(UserNotification::getIsDeleted, DataStatusConstants.NOT_DELETED)
                    .in(UserNotification::getId, ids)
                    .set(UserNotification::getIsDeleted, DataStatusConstants.DELETED)
                    .set(UserNotification::getUpdateTime, now));
        }

        NotificationBatchDeleteVO vo = new NotificationBatchDeleteVO();
        vo.setDeletedCount(deletedCount);
        vo.setUnreadCount(countUnread(userId, Collections.emptySet(), false));
        return vo;
    }

    private LambdaQueryWrapper<UserNotification> baseVisibleWrapper(Long userId) {
        return new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsDeleted, DataStatusConstants.NOT_DELETED);
    }

    private void applyListFilters(LambdaQueryWrapper<UserNotification> wrapper, String type, String tab) {
        String normalizedTab = tab != null ? tab.trim().toLowerCase(Locale.ROOT) : NotificationConstants.TAB_ALL;
        if (NotificationConstants.isUnreadTab(normalizedTab)) {
            wrapper.eq(UserNotification::getIsRead, UNREAD);
        }

        Set<String> typeFilter = NotificationConstants.resolveTypeFilter(type, tab);
        if (!typeFilter.isEmpty()) {
            wrapper.in(UserNotification::getType, typeFilter);
        }
    }

    private long countUnread(Long userId, Set<String> types, boolean restrictByTypes) {
        LambdaQueryWrapper<UserNotification> wrapper = baseVisibleWrapper(userId)
                .eq(UserNotification::getIsRead, UNREAD);
        if (restrictByTypes) {
            if (types == null || types.isEmpty()) {
                return 0L;
            }
            wrapper.in(UserNotification::getType, types);
        }
        return userNotificationMapper.selectCount(wrapper);
    }

    private Map<Long, User> loadActorMap(List<UserNotification> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> actorIds = rows.stream()
                .map(UserNotification::getActorId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        if (actorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userService.listByIds(actorIds);
        if (CollectionUtils.isEmpty(users)) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new HashMap<>();
        for (User user : users) {
            if (user != null && user.getId() != null) {
                map.put(user.getId(), user);
            }
        }
        return map;
    }

    private UserNotification requireOwnedNotification(Long userId, Long notificationId) {
        UserNotification row = userNotificationMapper.selectOne(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getId, notificationId)
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsDeleted, DataStatusConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "消息不存在或已删除");
        }
        return row;
    }

    private List<Long> parseIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ids = new LinkedHashSet<Long>();
        for (String raw : rawIds) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                long parsed = Long.parseLong(raw.trim());
                if (parsed > 0) {
                    ids.add(parsed);
                }
            } catch (NumberFormatException ignored) {
                // skip invalid id
            }
        }
        return new ArrayList<>(ids);
    }

    private void validateTab(String tab) {
        if (!StringUtils.hasText(tab)) {
            return;
        }
        String normalized = tab.trim().toLowerCase(Locale.ROOT);
        if (!NotificationConstants.knownTabs().contains(normalized)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "tab 参数不合法");
        }
    }

    /**
     * 全部已读仅支持 all / interact / system；空值视为 all。
     */
    private String normalizeReadAllTab(String tab) {
        if (!StringUtils.hasText(tab)) {
            return NotificationConstants.TAB_ALL;
        }
        String normalized = tab.trim().toLowerCase(Locale.ROOT);
        if (NotificationConstants.TAB_ALL.equals(normalized)
                || NotificationConstants.TAB_INTERACT.equals(normalized)
                || NotificationConstants.TAB_SYSTEM.equals(normalized)) {
            return normalized;
        }
        throw new BizException(Result.CODE_BAD_REQUEST, "tab 仅支持 all / interact / system");
    }

    private int toIntCount(long count) {
        if (count <= 0) {
            return 0;
        }
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) count;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }

    private void requireNotificationId(Long notificationId) {
        if (notificationId == null || notificationId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "消息 id 不合法");
        }
    }

}
