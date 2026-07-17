package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

@Data
public class NotificationUnreadCountVO {

    private Long total;

    private Long interact;

    private Long system;
}
