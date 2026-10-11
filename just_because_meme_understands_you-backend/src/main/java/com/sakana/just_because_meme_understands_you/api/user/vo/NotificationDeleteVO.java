package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

@Data
public class NotificationDeleteVO {

    private Long id;

    private Boolean deleted;

    private Long unreadCount;
}
