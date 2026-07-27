package com.sakana.just_because_meme_understands_you.task;

import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.service.meme.MemeCrawlService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时拉取 Firecrawl 热梗并写入业务库（默认每月 4 次）。
 */
@Slf4j
@Component
public class MemeCrawlTask {

    @Resource
    private MemeAgentProperties memeAgentProperties;

    @Resource
    private MemeCrawlService memeCrawlService;

    @Scheduled(cron = "${meme.crawl.cron:0 0 10 1,8,15,22 * *}")
    public void scheduledCrawl() {
        MemeAgentProperties.Crawl crawl = memeAgentProperties.getCrawl();
        if (crawl == null || !crawl.isEnabled() || !crawl.isScheduleEnabled()) {
            return;
        }
        try {
            memeCrawlService.crawlAndPersist();
        } catch (Exception e) {
            log.warn("定时热梗采集跳过/失败: {}", e.getMessage());
        }
    }
}
