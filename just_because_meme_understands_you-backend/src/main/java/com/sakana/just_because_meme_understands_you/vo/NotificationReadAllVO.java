package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * POST /notifications/read-all 响应。
 */
@Data
public class NotificationReadAllVO {

    /** 本次标记为已读的条数 */
    private Integer updatedCount;

    /** 标记后剩余未读总数 */
    private Integer unreadCount;

    /** 标记后互动类未读数 */
    private Integer interact;

    /** 标记后系统类未读数 */
    private Integer system;
}
