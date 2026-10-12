package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetBackfillState;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetReference;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetBackfillStateMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetLegacyReferenceMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetReferenceMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.LegacyFileAssetReferenceRow;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 中文注释：分批、幂等地将历史 OSS 文件字段转换为文件资产和业务引用。 */
@Slf4j
@Service
public class FileAssetLegacyBackfillRunner {

    /** 中文注释：本轮 OSS 文件生命周期回填使用的稳定任务键。 */
    static final String BACKFILL_KEY = "OSS_LEGACY_ASSET_V1";

    /** 中文注释：全局租约行名，确保同一时刻只有一个应用实例处理历史数据。 */
    private static final String RUNNER_SOURCE = "__runner__";

    /** 中文注释：按顺序逐项迁移的旧字段，名称必须与检查点状态行保持一致。 */
    private static final List<String> SOURCE_NAMES = List.of(
            "USER_AVATAR", "MEME_COVER", "MEME_RESOURCE", "COMMENT_IMAGE", "BANNER_IMAGE", "MEME_REVISION");

    /** 中文注释：应用侧限制每批可处理的旧业务记录上限，避免长事务。 */
    private static final int MAX_BATCH_SIZE = 1000;

    /** 中文注释：集中读取 OSS 清理开关、回填批次、租约和前缀规则。 */
    @Resource
    private OssCleanupProperties cleanupProperties;

    /** 中文注释：读取旧业务表的文件字段和待审核 JSON 快照。 */
    @Resource
    private FileAssetLegacyReferenceMapper legacyReferenceMapper;

    /** 中文注释：维护复合主键回填检查点和全局数据库租约。 */
    @Resource
    private FileAssetBackfillStateMapper backfillStateMapper;

    /** 中文注释：按唯一对象键幂等登记历史资产。 */
    @Resource
    private FileAssetMapper fileAssetMapper;

    /** 中文注释：按业务字段唯一键幂等创建历史文件引用。 */
    @Resource
    private FileAssetReferenceMapper fileAssetReferenceMapper;

    /** 中文注释：按当前 Bucket、上传域名和 CDN 配置识别并归一化 OSS URL。 */
    @Resource
    private OssUrlHelper ossUrlHelper;

    /** 中文注释：解析待审核修改稿中的图片和资源地址数组。 */
    @Resource
    private ObjectMapper objectMapper;

    /** 中文注释：按配置间隔推进一个源表批次，事务内提交引用和检查点。 */
    @Scheduled(fixedDelayString = "${oss.cleanup.backfill-poll-interval-ms:1000}")
    @Transactional
    public void runScheduledBackfill() {
        runNextBatch();
    }

    /**
     * 中文注释：领取全局数据库租约并处理一个数据源批次；可由测试直接调用。
     *
     * @return 本轮成功处理的旧业务记录数；租约未取得或任务关闭时返回0
     */
    @Transactional
    public int runNextBatch() {
        if (cleanupProperties == null || !cleanupProperties.isEnabled() || !cleanupProperties.isBackfillEnabled()) {
            return 0;
        }
        int batchSize = Math.min(MAX_BATCH_SIZE, Math.max(1, cleanupProperties.getBackfillBatchSize()));
        List<String> stateNames = new ArrayList<>(SOURCE_NAMES);
        stateNames.add(RUNNER_SOURCE);
        backfillStateMapper.ensureStateRows(BACKFILL_KEY, stateNames);

        String leaseOwner = StringUtils.hasText(cleanupProperties.getWorkerId())
                ? cleanupProperties.getWorkerId() : "oss-backfill-default";
        LocalDateTime leaseUntil = LocalDateTime.now()
                .plusSeconds(Math.max(30L, cleanupProperties.getBackfillLeaseSeconds()));
        if (backfillStateMapper.claimRunnerLease(BACKFILL_KEY, leaseOwner, leaseUntil) != 1) {
            return 0;
        }

        FileAssetBackfillState state = backfillStateMapper.selectNextIncompleteSource(BACKFILL_KEY);
        if (state == null) {
            backfillStateMapper.releaseRunnerLease(BACKFILL_KEY, leaseOwner, "COMPLETED", null);
            return 0;
        }

        String sourceName = state.getSourceName();
        List<LegacyFileAssetReferenceRow> rows = selectBatch(sourceName, state.getLastSourceId(), batchSize);
        long processed = 0;
        Long cursor = state.getLastSourceId();
        Map<String, FileAsset> assetCache = new HashMap<>();
        for (LegacyFileAssetReferenceRow row : rows) {
            if (row.getSourceId() != null && (cursor == null || row.getSourceId() > cursor)) {
                cursor = row.getSourceId();
            }
            if ("MEME_REVISION".equals(sourceName)) {
                backfillRevision(row, assetCache);
            } else {
                backfillValue(sourceName, row.getBizId(), row.getObjectValue(), defaultFieldKey(sourceName), assetCache);
            }
            processed++;
        }

        String sourceStatus = rows.size() < batchSize ? "COMPLETED" : "PENDING";
        backfillStateMapper.markSourceProgress(BACKFILL_KEY, sourceName, cursor, sourceStatus);
        String runnerStatus = backfillStateMapper.countIncompleteSourceStates(BACKFILL_KEY) == 0
                ? "COMPLETED" : "PENDING";
        backfillStateMapper.releaseRunnerLease(BACKFILL_KEY, leaseOwner, runnerStatus, null);
        if ("COMPLETED".equals(runnerStatus)) {
            log.info("OSS 历史文件资产回填完成 backfillKey={}, processedRows={}", BACKFILL_KEY, processed);
        } else {
            log.info("OSS 历史文件资产回填批次完成 backfillKey={}, source={}, processedRows={}, nextCursor={}",
                    BACKFILL_KEY, sourceName, processed, cursor);
        }
        return Math.toIntExact(processed);
    }

    /** 中文注释：按数据源选择对应的旧表查询；每个查询都使用主键游标和有界批次。 */
    private List<LegacyFileAssetReferenceRow> selectBatch(String sourceName, Long lastSourceId, int batchSize) {
        return switch (sourceName) {
            case "USER_AVATAR" -> legacyReferenceMapper.selectUserAvatarBatch(lastSourceId, batchSize);
            case "MEME_COVER" -> legacyReferenceMapper.selectMemeCoverBatch(lastSourceId, batchSize);
            case "MEME_RESOURCE" -> legacyReferenceMapper.selectMemeResourceBatch(lastSourceId, batchSize);
            case "COMMENT_IMAGE" -> legacyReferenceMapper.selectCommentImageBatch(lastSourceId, batchSize);
            case "BANNER_IMAGE" -> legacyReferenceMapper.selectBannerImageBatch(lastSourceId, batchSize);
            case "MEME_REVISION" -> legacyReferenceMapper.selectMemeRevisionBatch(lastSourceId, batchSize);
            default -> throw new IllegalStateException("未知的 OSS 历史回填数据源：" + sourceName);
        };
    }

    /** 中文注释：解析待审核 JSON 中的封面、媒体和结构化链接，保持数组索引作为资源字段键。 */
    private void backfillRevision(LegacyFileAssetReferenceRow row, Map<String, FileAsset> assetCache) {
        if (row.getBizId() == null || !StringUtils.hasText(row.getContentJson())) {
            return;
        }
        try {
            JsonNode content = objectMapper.readTree(row.getContentJson());
            JsonNode cover = content.path("image");
            if (cover.isTextual()) {
                backfillValue("MEME_REVISION", row.getBizId(), cover.asText(), "COVER", assetCache);
            }
            JsonNode resources = content.path("resourceUrls");
            if (resources.isArray()) {
                for (int index = 0; index < resources.size(); index++) {
                    JsonNode value = resources.get(index);
                    if (value != null && value.isTextual()) {
                        backfillValue("MEME_REVISION", row.getBizId(), value.asText(),
                                "RESOURCE:" + index, assetCache);
                    }
                }
            }
            JsonNode links = content.path("resources");
            if (links.isArray()) {
                for (int index = 0; index < links.size(); index++) {
                    JsonNode url = links.get(index).path("url");
                    if (url.isTextual()) {
                        backfillValue("MEME_REVISION", row.getBizId(), url.asText(),
                                "LINK:" + index, assetCache);
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("跳过无法解析的待审核梗修改稿文件快照 revisionId={}", row.getBizId(), exception);
        }
    }

    /** 中文注释：只为自有且位于允许前缀内的对象登记资产，并建立业务字段引用。 */
    private void backfillValue(String sourceName, Long bizId, String rawValue, String fieldKey,
                               Map<String, FileAsset> assetCache) {
        if (bizId == null || !StringUtils.hasText(rawValue)) {
            return;
        }
        String objectKey = normalizeOwnedObjectKey(rawValue);
        if (objectKey == null) {
            return;
        }

        FileAsset asset = assetCache.get(objectKey);
        if (asset == null) {
            String scene = sceneForObjectKey(objectKey);
            fileAssetMapper.insertLegacyAssetIfAbsent(objectKey, scene);
            asset = fileAssetMapper.selectByObjectKey(objectKey);
            if (asset == null || "DELETING".equals(asset.getStatus()) || "DELETED".equals(asset.getStatus())) {
                log.warn("历史文件引用指向不可重新激活的资产，跳过该引用 source={}, bizId={}, objectKey={}",
                        sourceName, bizId, objectKey);
                return;
            }
            assetCache.put(objectKey, asset);
        }

        FileAssetReference reference = new FileAssetReference();
        reference.setAssetId(asset.getId());
        reference.setBizType(bizTypeForSource(sourceName));
        reference.setBizId(bizId);
        reference.setFieldKey(fieldKey);
        fileAssetReferenceMapper.insertIgnoreLegacyReference(reference);
    }

    /** 中文注释：将当前 OSS/CDN 完整 URL 或对象键转换成可管理的对象键；外链和受保护键返回null。 */
    String normalizeOwnedObjectKey(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String objectKey = ossUrlHelper.normalizeForStorage(rawValue.trim());
        if (!StringUtils.hasText(objectKey) || objectKey.contains("://") || objectKey.length() > 512
                || objectKey.startsWith("/") || objectKey.indexOf('\\') >= 0
                || objectKey.codePoints().anyMatch(Character::isISOControl)) {
            return null;
        }
        for (String segment : objectKey.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        List<String> protectedPrefixes = cleanupProperties.getProtectedPrefixes();
        if (protectedPrefixes != null && protectedPrefixes.stream().filter(StringUtils::hasText)
                .map(prefix -> prefix.toLowerCase(Locale.ROOT))
                .anyMatch(objectKey.toLowerCase(Locale.ROOT)::startsWith)) {
            return null;
        }
        List<String> allowedPrefixes = cleanupProperties.getAllowedPrefixes();
        if (allowedPrefixes == null || allowedPrefixes.stream().filter(StringUtils::hasText)
                .noneMatch(objectKey::startsWith)) {
            return null;
        }
        return objectKey;
    }

    /** 中文注释：旧业务字段与新多态引用中的字段类型一一对应。 */
    private String defaultFieldKey(String sourceName) {
        return switch (sourceName) {
            case "USER_AVATAR" -> "AVATAR";
            case "MEME_COVER" -> "COVER";
            case "MEME_RESOURCE", "COMMENT_IMAGE" -> "URL";
            case "BANNER_IMAGE" -> "IMAGE";
            default -> throw new IllegalStateException("未知的 OSS 历史回填字段：" + sourceName);
        };
    }

    /** 中文注释：历史来源表映射为 file_asset_ref.biz_type。 */
    private String bizTypeForSource(String sourceName) {
        return switch (sourceName) {
            case "USER_AVATAR" -> "USER";
            case "MEME_COVER" -> "MEME";
            case "MEME_RESOURCE" -> "MEME_RESOURCE";
            case "COMMENT_IMAGE" -> "COMMENT_IMAGE";
            case "BANNER_IMAGE" -> "BANNER";
            case "MEME_REVISION" -> "MEME_REVISION";
            default -> throw new IllegalStateException("未知的 OSS 历史回填类型：" + sourceName);
        };
    }

    /** 中文注释：从历史对象键前缀推导资产场景，无法识别的旧前缀统一标记legacy。 */
    private String sceneForObjectKey(String objectKey) {
        String normalized = objectKey.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("tmp/avatar/")) return "avatar";
        if (normalized.startsWith("tmp/meme/") || normalized.startsWith("tmp/memes/")) return "meme";
        if (normalized.startsWith("tmp/comment/") || normalized.startsWith("tmp/comments/")) return "comment";
        if (normalized.startsWith("tmp/home/")) return "home";
        if (normalized.startsWith("avatar/")) return "avatar";
        if (normalized.startsWith("memes/") || normalized.startsWith("meme/")) return "meme";
        if (normalized.startsWith("comments/") || normalized.startsWith("comment/")) return "comment";
        if (normalized.startsWith("home/")) return "home";
        return "legacy";
    }
}
