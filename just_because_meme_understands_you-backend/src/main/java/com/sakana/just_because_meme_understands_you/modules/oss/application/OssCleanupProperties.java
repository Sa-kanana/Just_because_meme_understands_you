package com.sakana.just_because_meme_understands_you.modules.oss.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 中文注释：集中配置 OSS 文件删除、临时资产和孤儿对账的安全参数。 */
@Data
@Component
@ConfigurationProperties(prefix = "oss.cleanup")
public class OssCleanupProperties {

    /** 中文注释：是否启用 OSS 延迟清理定时任务。 */
    private boolean enabled = true;

    /** 中文注释：单轮任务最多领取的删除任务数量。 */
    private int batchSize = 100;

    /** 中文注释：Worker 两次轮询之间的固定间隔，单位为毫秒。 */
    private long pollIntervalMs = 60_000L;

    /** 中文注释：最后一个业务引用释放后等待 OSS 删除的缓冲天数。 */
    private long deletionDelayDays = 2L;

    /** 中文注释：OSS 删除失败后最多安排的重试次数。 */
    private int maxRetries = 5;

    /** 中文注释：Worker 领取任务后持有处理租约的时长，单位为秒。 */
    private long leaseSeconds = 300L;

    /** 中文注释：多实例共享该标识时应显式配置唯一实例名；默认每次启动生成随机值。 */
    private String workerId = "oss-cleanup-" + UUID.randomUUID();

    /** 中文注释：尚未确认的临时上传资产保留天数，过期后进入删除队列。 */
    private long tempAssetTtlDays = 3L;

    /** 中文注释：孤儿对象至少经过该观察天数后才能进入人工复查阶段。 */
    private long orphanReviewDays = 7L;

    /** 中文注释：孤儿对象清理是否默认仅记录候选而不创建删除任务。 */
    private boolean reconcileDryRun = true;

    /** 中文注释：允许 Worker 删除的 OSS 对象键前缀白名单。 */
    private List<String> allowedPrefixes = new ArrayList<>(List.of(
            "tmp/", "avatar/", "memes/", "comments/", "home/", "common/"));

    /** 中文注释：即使出现在允许列表中也始终禁止删除的 OSS 对象键前缀。 */
    private List<String> protectedPrefixes = new ArrayList<>(List.of("system/", "homepage/static/"));
}
