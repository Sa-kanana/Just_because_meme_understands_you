package com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.task;

import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.MemeTrendRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
public class MemeTrendSnapshotTask {

    private final MemeTrendRepository repository;

    public MemeTrendSnapshotTask(MemeTrendRepository repository) {
        this.repository = repository;
    }

    /** 启动时先建立当天基线，让趋势数据从部署当天开始累积。 */
    @EventListener(ApplicationReadyEvent.class)
    public void captureStartupBaseline() {
        captureDailySnapshot();
    }

    /** 每日留存公开梗的计数基线，重复执行同一天会覆盖当天快照。 */
    @Scheduled(cron = "${meme.trends.snapshot-cron:0 5 0 * * *}", zone = "Asia/Shanghai")
    public void captureDailySnapshot() {
        try {
            int affected = repository.capture(LocalDate.now(ZoneId.of("Asia/Shanghai")));
            log.info("热度日快照完成，处理记录数={}", affected);
        } catch (Exception e) {
            log.warn("热度日快照失败", e);
        }
    }
}
