package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

@Data
public class NotificationItemVO {

    private Long id;

    private String type;

    private String title;

    private String content;

    private Boolean isRead;

    private String createTime;

    private NotificationActorVO actor;

    private String targetType;

    private Long targetId;

    private Long refId;

    private NotificationJumpVO jump;

    private NotificationExtraVO extra;
}
