package com.sakana.just_because_meme_understands_you.modules.comment.contract;

/** 评论模块向其他业务模块开放的审核操作契约。 */
public interface CommentModerationPort {

    /** 软删除指定评论；删除根评论时同时隐藏其回复，并同步梗图评论总数。 */
    int hideComment(long commentId);
}
