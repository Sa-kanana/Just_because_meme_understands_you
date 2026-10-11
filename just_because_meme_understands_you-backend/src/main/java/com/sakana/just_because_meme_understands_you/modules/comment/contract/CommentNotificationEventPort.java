package com.sakana.just_because_meme_understands_you.modules.comment.contract;

/** 评论模块记录需要异步投递的通知事件。 */
public interface CommentNotificationEventPort {

    /** 在调用方事务中持久化通知事件。 */
    void append(CommentNotificationEvent event);
}
