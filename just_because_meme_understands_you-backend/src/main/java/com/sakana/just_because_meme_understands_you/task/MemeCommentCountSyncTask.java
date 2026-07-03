package com.sakana.just_because_meme_understands_you.task;

import com.sakana.just_because_meme_understands_you.service.comment.MemeCommentCountService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 将 Redis 中待同步的梗评论总数批量刷回 meme.comments（定时对账）。
 */
@Component
public class MemeCommentCountSyncTask {

    @Resource
    private MemeCommentCountService memeCommentCountService;

    @Scheduled(fixedDelayString = "${meme-comment.count-sync-interval-ms:120000}")
    public void syncCommentCountToDb() {
        memeCommentCountService.syncPendingToDb();
    }
}
