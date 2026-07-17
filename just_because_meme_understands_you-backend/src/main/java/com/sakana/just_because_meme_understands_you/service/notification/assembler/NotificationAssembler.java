package com.sakana.just_because_meme_understands_you.service.notification.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.constant.NotificationConstants;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.entity.UserNotification;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.vo.NotificationActorVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationExtraVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationItemVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationJumpVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NotificationAssembler {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int READ = 1;

    private final ObjectMapper objectMapper;
    private final OssUrlHelper ossUrlHelper;

    public NotificationAssembler(ObjectMapper objectMapper, OssUrlHelper ossUrlHelper) {
        this.objectMapper = objectMapper;
        this.ossUrlHelper = ossUrlHelper;
    }

    public List<NotificationItemVO> toItemList(List<UserNotification> rows, Map<Long, User> actorMap) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(row -> toItem(row, actorMap))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public NotificationItemVO toItem(UserNotification row, Map<Long, User> actorMap) {
        if (row == null) {
            return null;
        }
        NotificationItemVO vo = new NotificationItemVO();
        vo.setId(row.getId());
        vo.setType(row.getType());
        vo.setTitle(row.getTitle());
        vo.setContent(row.getContent());
        vo.setIsRead(row.getIsRead() != null && row.getIsRead() == READ);
        vo.setCreateTime(formatTime(row.getCreateTime()));
        vo.setTargetType(row.getTargetType());
        vo.setTargetId(row.getTargetId());
        vo.setRefId(row.getRefId());
        vo.setActor(buildActor(row.getActorId(), actorMap));
        vo.setExtra(parseExtra(row.getExtraJson()));
        vo.setJump(buildJump(row));
        return vo;
    }

    private NotificationActorVO buildActor(Long actorId, Map<Long, User> actorMap) {
        if (actorId == null || actorId <= 0) {
            return null;
        }
        User user = actorMap != null ? actorMap.get(actorId) : null;
        NotificationActorVO actor = new NotificationActorVO();
        actor.setUserId(actorId);
        actor.setNickname(user != null && StringUtils.hasText(user.getNickname()) ? user.getNickname() : "梗友");
        actor.setAvatar(user != null ? ossUrlHelper.toPublicUrl(user.getAvatar()) : "");
        return actor;
    }

    private NotificationExtraVO parseExtra(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return null;
        }
        try {
            Map<String, String> raw = objectMapper.readValue(extraJson, new TypeReference<>() {
            });
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            NotificationExtraVO extra = new NotificationExtraVO();
            extra.setMemeName(raw.get("memeName"));
            String cover = raw.get("memeCover");
            extra.setMemeCover(StringUtils.hasText(cover) ? ossUrlHelper.toPublicUrl(cover) : "");
            if (!StringUtils.hasText(extra.getMemeName()) && !StringUtils.hasText(extra.getMemeCover())) {
                return null;
            }
            return extra;
        } catch (Exception e) {
            log.debug("解析消息 extra_json 失败: {}", extraJson);
            return null;
        }
    }

    private NotificationJumpVO buildJump(UserNotification row) {
        if (row == null) {
            return null;
        }
        String targetType = row.getTargetType() != null ? row.getTargetType().trim().toLowerCase() : "";
        String type = row.getType() != null ? row.getType().trim().toLowerCase() : "";

        if (NotificationConstants.TARGET_MEME.equals(targetType) && row.getTargetId() != null && row.getTargetId() > 0) {
            NotificationJumpVO jump = new NotificationJumpVO();
            jump.setName(NotificationConstants.JUMP_MEME_DETAIL);
            Map<String, String> params = new HashMap<>();
            params.put("id", String.valueOf(row.getTargetId()));
            jump.setParams(params);
            if (row.getRefId() != null && row.getRefId() > 0
                    && ("comment".equals(type) || "reply".equals(type) || "comment_like".equals(type))) {
                Map<String, String> query = new HashMap<>();
                query.put("commentId", String.valueOf(row.getRefId()));
                jump.setQuery(query);
            }
            return jump;
        }

        if (NotificationConstants.TARGET_USER.equals(targetType) && row.getTargetId() != null && row.getTargetId() > 0) {
            NotificationJumpVO jump = new NotificationJumpVO();
            jump.setName(NotificationConstants.JUMP_USER_PROFILE);
            Map<String, String> params = new HashMap<>();
            params.put("userId", String.valueOf(row.getTargetId()));
            jump.setParams(params);
            return jump;
        }

        if ("follow".equals(type) && row.getActorId() != null && row.getActorId() > 0) {
            NotificationJumpVO jump = new NotificationJumpVO();
            jump.setName(NotificationConstants.JUMP_USER_PROFILE);
            Map<String, String> params = new HashMap<>();
            params.put("userId", String.valueOf(row.getActorId()));
            jump.setParams(params);
            return jump;
        }

        return null;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FMT.format(time);
    }
}
