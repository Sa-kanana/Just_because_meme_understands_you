package com.sakana.just_because_meme_understands_you.task;

import com.sakana.just_because_meme_understands_you.service.user.UserFavoriteCountService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserFavoriteCountSyncTask {

    @Resource
    private UserFavoriteCountService userFavoriteCountService;

    @Scheduled(fixedDelayString = "${user-favorite.count-sync-interval-ms:120000}")
    public void syncFavoriteCountToDb() {
        userFavoriteCountService.syncPendingToDb();
    }
}
