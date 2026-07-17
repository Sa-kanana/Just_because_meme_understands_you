package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量删除 / 清空消息请求。
 */
@Data
public class NotificationBatchDeleteRequestDTO {

    /** 指定删除的消息 id 列表 */
    private List<String> ids;

    /** 清空全部消息 */
    private Boolean clearAll;

    /** 仅清空已读消息 */
    private Boolean clearReadOnly;
}
