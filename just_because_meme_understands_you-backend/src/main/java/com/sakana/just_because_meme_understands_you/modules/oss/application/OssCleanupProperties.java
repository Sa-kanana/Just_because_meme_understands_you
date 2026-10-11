package com.sakana.just_because_meme_understands_you.modules.oss.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /** 中文注释：是否启动历史 OSS 文件引用回填；关闭时物理删除门禁保持关闭。 */
    private boolean backfillEnabled = true;

    /** 中文注释：每个历史数据源单批读取和保存的最大记录数。 */
    private int backfillBatchSize = 200;

    /** 中文注释：历史文件引用回填批次之间的调度间隔，单位为毫秒。 */
    private long backfillPollIntervalMs = 1000L;

    /** 中文注释：历史回填全局租约有效时长，单位为秒。 */
    private long backfillLeaseSeconds = 300L;

    /** 中文注释：孤儿对象至少经过该观察天数后才能进入人工复查阶段。 */
    private long orphanReviewDays = 7L;

    /** 中文注释：孤儿对象清理是否默认仅记录候选而不创建删除任务。 */
    private boolean reconcileDryRun = true;

    /** 中文注释：是否启用每日OSS孤儿对象列举；默认关闭以避免意外扫描整个Bucket。 */
    private boolean reconcileEnabled = false;

    /** 中文注释：每页最多列举的OSS对象数量，最大不超过OSS接口允许的1000个。 */
    private int reconcilePageSize = 500;

    /** 中文注释：孤儿对账仅扫描这些业务前缀，仍需同时满足删除白名单。 */
    private List<String> reconcilePrefixes = new ArrayList<>(List.of(
            "tmp/", "avatar/", "memes/", "comments/", "home/", "common/"));

    /** 中文注释：允许 Worker 删除的 OSS 对象键前缀白名单。 */
    private List<String> allowedPrefixes = new ArrayList<>(List.of(
            "tmp/", "avatar/", "memes/", "comments/", "home/", "common/"));

    /** 中文注释：即使出现在允许列表中也始终禁止删除的 OSS 对象键前缀。 */
    private List<String> protectedPrefixes = new ArrayList<>(List.of("system/", "homepage/static/"));

    /** 中文注释：统一校验对账与Worker的对象键安全规则，防止前缀绕过和路径穿越。 */
    public boolean isSafeDeleteKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.length() > 512
                || objectKey.startsWith("/") || objectKey.indexOf((char) 92) >= 0
                || objectKey.contains("://") || objectKey.codePoints().anyMatch(Character::isISOControl)) {
            return false;
        }
        String normalized = objectKey.toLowerCase(Locale.ROOT);
        if (protectedPrefixes != null && protectedPrefixes.stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .map(prefix -> prefix.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::startsWith)) {
            return false;
        }
        if (allowedPrefixes == null || allowedPrefixes.stream()
                .filter(prefix -> prefix != null && prefix.endsWith("/"))
                .noneMatch(objectKey::startsWith)) {
            return false;
        }
        for (String segment : objectKey.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }
}
