package com.sakana.just_because_meme_understands_you.service.user.support;

import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import com.sakana.just_because_meme_understands_you.vo.AuthorVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 梗作者信息组装：批量查用户，避免 N+1。
 */
@Component
public class AuthorSupport {

    private static final String FALLBACK_NICKNAME = "匿名用户";

    @Resource
    private IUserService userService;

    @Resource
    private OssUrlHelper ossUrlHelper;

    public Map<Long, User> loadUserMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userService.listByIds(ids);
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

    /**
     * @param includeSignature 列表场景可传 false（对齐 /list 不返回签名）
     */
    public AuthorVO toAuthorVO(Long userId, User user, boolean includeSignature) {
        AuthorVO vo = new AuthorVO();
        if (userId != null && userId > 0) {
            vo.setUserId(String.valueOf(userId));
        } else if (user != null && user.getId() != null) {
            vo.setUserId(String.valueOf(user.getId()));
        } else {
            vo.setUserId("");
        }
        if (user == null) {
            vo.setNickname(FALLBACK_NICKNAME);
            vo.setAvatar("");
            if (includeSignature) {
                vo.setSignature("");
            }
            return vo;
        }
        vo.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname().trim() : FALLBACK_NICKNAME);
        vo.setAvatar(ossUrlHelper.toPublicUrl(user.getAvatar()));
        if (includeSignature) {
            vo.setSignature(user.getSignature() == null ? "" : user.getSignature().trim());
        }
        return vo;
    }

    public AuthorVO toAuthorVO(Long userId, Map<Long, User> userMap, boolean includeSignature) {
        User user = userId == null ? null : userMap.get(userId);
        return toAuthorVO(userId, user, includeSignature);
    }
}
