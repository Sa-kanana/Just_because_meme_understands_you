package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

@Data
public class NotificationReadVO {

    private Long id;

    private Boolean isRead;

    private Long unreadCount;
}
