package com.sakana.just_because_meme_understands_you.modules.meme.contract;

/** 梗图发布状态变化的集成端口；事件写入由组合层实现。 */
public interface MemeIndexEventPort {

    /** 在当前梗图事务中记录事件，提交后同步其当前公开内容到 AI 索引。 */
    void requestUpsert(long memeId);

    /** 在当前梗图事务中记录事件，提交后按当前状态更新 AI 索引。 */
    void requestRemoval(long memeId);
}
