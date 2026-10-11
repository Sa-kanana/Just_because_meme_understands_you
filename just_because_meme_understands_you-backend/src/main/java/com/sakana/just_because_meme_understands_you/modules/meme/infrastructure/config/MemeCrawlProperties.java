package com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 梗图采集和首页实时梗图展示配置，由梗图模块独立持有。 */
@Data
@Component
@ConfigurationProperties(prefix = "meme.crawl")
public class MemeCrawlProperties {

    /** 是否启用热梗采集功能。 */
    private boolean enabled = true;

    /** 是否启用定时采集任务。 */
    private boolean scheduleEnabled = true;

    /** 定时采集表达式，默认每月 1、8、15、22 日上午 10 点执行。 */
    private String cron = "0 0 10 1,8,15,22 * *";

    /** 每次定时采集的候选梗图数量。 */
    private int limit = 5;

    /** 发布采集梗图使用的系统用户 ID。 */
    private long publisherUserId = 0L;

    /** 首页实时梗图最多展示数量。 */
    private int liveLimit = 8;

    /** 首页实时梗图时间范围，单位为小时。 */
    private int liveHours = 48;
}
