package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.FileAssetStatus;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetReference;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetDeleteTaskMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetReferenceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** 中文注释：统一管理 OSS 文件资产提升、业务引用替换和延迟删除入队。 */
@Service
public class FileAssetService {

    /** 中文注释：集中读取文件资产删除缓冲期和临时资产有效期配置。 */
    @Resource
    private OssCleanupProperties cleanupProperties;

    /** 中文注释：锁定并更新文件资产元数据及生命周期状态的映射器。 */
    @Resource
    private FileAssetMapper fileAssetMapper;

    /** 中文注释：锁定和维护资产到业务字段引用关系的映射器。 */
    @Resource
    private FileAssetReferenceMapper fileAssetReferenceMapper;

    /** 中文注释：创建延迟删除任务并撤销尚未执行任务的映射器。 */
    @Resource
    private FileAssetDeleteTaskMapper fileAssetDeleteTaskMapper;

    /** 中文注释：兼容现有头像、梗图、评论图和首页图的安全对象提升逻辑。 */
    @Resource
    private OssObjectPromoteService ossObjectPromoteService;

    /** 中文注释：归一化 OSS/CDN URL 并识别非本站外链的工具。 */
    @Resource
    private OssUrlHelper ossUrlHelper;

    /**
     * 中文注释：登记服务端 multipart 上传成功的临时资产；该对象已由服务端确认上传完成。
     *
     * @param ownerId 当前上传用户ID
     * @param objectKey 服务端写入 OSS 的临时对象键
     * @param scene 上传业务场景
     * @param originalFilename 上传文件的原始文件名
     * @param contentType 上传文件的 MIME 类型
     * @param fileSize 上传文件的字节数
     */
    @Transactional
    public void registerServerUploadedTempAsset(Long ownerId, String objectKey, String scene,
                                                String originalFilename, String contentType, Long fileSize) {
        if (ownerId == null || ownerId <= 0 || !StringUtils.hasText(scene) || scene.length() > 32
                || !StringUtils.hasText(objectKey) || !objectKey.startsWith("tmp/" + scene + "/" + ownerId + "/")
                || objectKey.length() > 512 || objectKey.contains("..") || objectKey.contains("\\")
                || objectKey.codePoints().anyMatch(Character::isISOControl)
                || fileSize == null || fileSize <= 0
                || fileSize > 10L * 1024 * 1024) {
            throw new BizException(Result.CODE_BAD_REQUEST, "服务端上传文件资产参数不合法");
        }
        String filename = normalizeFilename(originalFilename);
        if (StringUtils.hasText(contentType) && contentType.length() > 128) {
            throw new BizException(Result.CODE_BAD_REQUEST, "上传文件 MIME 类型不合法");
        }
        LocalDateTime now = LocalDateTime.now();
        FileAsset asset = new FileAsset();
        asset.setObjectKey(objectKey);
        asset.setOwnerId(ownerId);
        asset.setScene(scene);
        asset.setOriginalFilename(filename);
        asset.setContentType(StringUtils.hasText(contentType) ? contentType.trim() : null);
        asset.setFileSize(fileSize);
        asset.setStatus(FileAssetStatus.TEMP.name());
        asset.setUploadedAt(now);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        if (fileAssetMapper.insert(asset) != 1 || asset.getId() == null) {
            throw new BizException(Result.CODE_ERROR, "登记服务端上传文件失败，请重试");
        }
    }

    /**
     * 中文注释：验证临时资产归属、确认状态和有效期后，将对象复制到正式业务前缀。
     *
     * @param keyOrUrl 客户端提交的 OSS 对象键或本站访问 URL
     * @param uploaderId 当前上传者用户ID
     * @param scene 上传业务场景
     * @return 正式 OSS 对象键；外部链接保持原样
     */
    @Transactional
    public String promote(String keyOrUrl, Long uploaderId, String scene) {
        if (!StringUtils.hasText(keyOrUrl)) {
            return keyOrUrl;
        }
        String objectKey = ossUrlHelper.normalizeForStorage(keyOrUrl);
        if (isExternalUrl(objectKey)) {
            return objectKey;
        }
        if (uploaderId == null || uploaderId <= 0 || !StringUtils.hasText(scene)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "文件资产上传者或场景不合法");
        }
        FileAsset asset = fileAssetMapper.selectByObjectKeyForUpdate(objectKey);
        if (asset == null || !uploaderId.equals(asset.getOwnerId()) || !scene.equals(asset.getScene())) {
            throw new BizException(Result.CODE_FORBIDDEN, "文件资产不存在或不属于当前上传者");
        }
        if (FileAssetStatus.ACTIVE.name().equals(asset.getStatus()) && !objectKey.startsWith("tmp/")) {
            return objectKey;
        }
        if (!FileAssetStatus.TEMP.name().equals(asset.getStatus()) || asset.getUploadedAt() == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "临时文件尚未确认上传，不能绑定业务");
        }
        LocalDateTime now = LocalDateTime.now();
        if (asset.getCreatedAt() == null
                || asset.getCreatedAt().plusDays(Math.max(1L, cleanupProperties.getTempAssetTtlDays())).isBefore(now)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "临时文件已过期，请重新上传");
        }
        if (!objectKey.startsWith("tmp/" + scene + "/" + uploaderId + "/")) {
            throw new BizException(Result.CODE_BAD_REQUEST, "临时文件路径与上传者或业务场景不匹配");
        }

        String promotedKey = switch (scene.toLowerCase(Locale.ROOT)) {
            case "avatar" -> ossObjectPromoteService.promoteAvatar(objectKey, uploaderId);
            case "meme" -> ossObjectPromoteService.promoteMemeImage(objectKey, uploaderId);
            case "comment" -> ossObjectPromoteService.promoteCommentImage(objectKey, uploaderId);
            case "home" -> ossObjectPromoteService.promoteHomeImage(objectKey, uploaderId);
            default -> throw new BizException(Result.CODE_BAD_REQUEST, "不支持的文件资产业务场景");
        };
        asset.setObjectKey(ossUrlHelper.normalizeForStorage(promotedKey));
        asset.setStatus(FileAssetStatus.ACTIVE.name());
        asset.setActivatedAt(now);
        asset.setUpdatedAt(now);
        if (fileAssetMapper.updateById(asset) != 1) {
            throw new BizException(Result.CODE_ERROR, "保存文件资产正式路径失败，请重试");
        }
        return asset.getObjectKey();
    }

    /** 中文注释：在当前业务事务中绑定新对象并释放同一业务字段之前的资产引用。 */
    @Transactional
    public void replaceReference(String objectKeyOrUrl, String bizType, Long bizId, String fieldKey, String reason) {
        validateBusinessField(bizType, bizId, fieldKey);
        String normalizedKey = StringUtils.hasText(objectKeyOrUrl)
                ? ossUrlHelper.normalizeForStorage(objectKeyOrUrl.trim())
                : null;
        boolean externalUrl = isExternalUrl(normalizedKey);
        FileAsset newAssetSnapshot = null;
        if (StringUtils.hasText(normalizedKey) && !externalUrl) {
            newAssetSnapshot = fileAssetMapper.selectByObjectKey(normalizedKey);
            if (newAssetSnapshot == null) {
                throw new BizException(Result.CODE_BAD_REQUEST, "OSS 文件尚未登记为可管理资产");
            }
        }

        List<FileAssetReference> previousReferences = fileAssetReferenceMapper.selectByBusinessFieldForUpdate(
                bizType, bizId, fieldKey);
        Set<Long> assetIds = new TreeSet<>();
        previousReferences.stream().map(FileAssetReference::getAssetId).filter(Objects::nonNull).forEach(assetIds::add);
        if (newAssetSnapshot != null) {
            assetIds.add(newAssetSnapshot.getId());
        }
        Map<Long, FileAsset> lockedAssets = lockAssets(assetIds);
        FileAsset newAsset = newAssetSnapshot == null ? null : lockedAssets.get(newAssetSnapshot.getId());
        if (newAssetSnapshot != null && (newAsset == null || !normalizedKey.equals(newAsset.getObjectKey()))) {
            throw new BizException(Result.CODE_BAD_REQUEST, "OSS 文件资产在更新期间发生变化，请重试");
        }
        validateBindableAsset(newAsset);

        if (newAsset != null && FileAssetStatus.ACTIVE.name().equals(newAsset.getStatus())
                && previousReferences.size() == 1
                && newAsset.getId().equals(previousReferences.get(0).getAssetId())) {
            return;
        }

        Set<Long> previousAssetIds = new TreeSet<>();
        previousReferences.stream().map(FileAssetReference::getAssetId)
                .filter(Objects::nonNull).forEach(previousAssetIds::add);
        if (newAsset == null) {
            deletePreviousReferences(previousReferences, lockedAssets, previousAssetIds, reason);
            return;
        }

        activateAsset(newAsset);
        rebindBusinessField(previousReferences, newAsset, bizType, bizId, fieldKey);
        releaseUnreferencedAssets(previousAssetIds, lockedAssets, newAsset.getId(), reason);
    }

    /** 中文注释：释放指定业务字段引用；资产只在引用计数归零后进入延迟删除队列。 */
    @Transactional
    public void releaseReference(String bizType, Long bizId, String fieldKey, String reason) {
        replaceReference(null, bizType, bizId, fieldKey, reason);
    }

    private void deletePreviousReferences(List<FileAssetReference> references,
                                          Map<Long, FileAsset> lockedAssets,
                                          Set<Long> assetIds,
                                          String reason) {
        for (FileAssetReference reference : references) {
            if (fileAssetReferenceMapper.deleteById(reference.getId()) != 1) {
                throw new BizException(Result.CODE_ERROR, "释放文件资产引用失败，请重试");
            }
        }
        releaseUnreferencedAssets(assetIds, lockedAssets, null, reason);
    }

    /** 中文注释：先激活新资产，再原位切换唯一业务引用，避免新旧对象引用之间出现空档。 */
    private void rebindBusinessField(List<FileAssetReference> references, FileAsset newAsset,
                                    String bizType, Long bizId, String fieldKey) {
        LocalDateTime now = LocalDateTime.now();
        if (references.isEmpty()) {
            FileAssetReference reference = new FileAssetReference();
            reference.setAssetId(newAsset.getId());
            reference.setBizType(bizType);
            reference.setBizId(bizId);
            reference.setFieldKey(fieldKey);
            reference.setCreatedAt(now);
            if (fileAssetReferenceMapper.insert(reference) != 1) {
                throw new BizException(Result.CODE_ERROR, "创建文件资产业务引用失败，请重试");
            }
            return;
        }

        FileAssetReference currentReference = references.get(0);
        if (!newAsset.getId().equals(currentReference.getAssetId())) {
            currentReference.setAssetId(newAsset.getId());
            currentReference.setCreatedAt(now);
            if (fileAssetReferenceMapper.updateById(currentReference) != 1) {
                throw new BizException(Result.CODE_ERROR, "切换文件资产业务引用失败，请重试");
            }
        }
        for (int index = 1; index < references.size(); index++) {
            if (fileAssetReferenceMapper.deleteById(references.get(index).getId()) != 1) {
                throw new BizException(Result.CODE_ERROR, "清理重复文件资产业务引用失败，请重试");
            }
        }
    }

    /** 中文注释：只有业务引用归零且资产不是新绑定对象时，才创建延迟删除任务。 */
    private void releaseUnreferencedAssets(Set<Long> assetIds, Map<Long, FileAsset> lockedAssets,
                                           Long retainedAssetId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        for (Long assetId : assetIds) {
            if (Objects.equals(assetId, retainedAssetId)) {
                continue;
            }
            FileAsset asset = lockedAssets.get(assetId);
            if (asset == null || !FileAssetStatus.ACTIVE.name().equals(asset.getStatus())) {
                continue;
            }
            if (fileAssetReferenceMapper.countByAssetId(assetId) > 0) {
                continue;
            }
            asset.setStatus(FileAssetStatus.PENDING_DELETE.name());
            asset.setDeleteAfter(now.plusDays(Math.max(0L, cleanupProperties.getDeletionDelayDays())));
            asset.setUpdatedAt(now);
            if (fileAssetMapper.updateById(asset) != 1) {
                throw new BizException(Result.CODE_ERROR, "更新文件资产删除状态失败，请重试");
            }
            insertDeleteTask(asset, reason, now);
        }
    }

    private void activateAsset(FileAsset asset) {
        if (FileAssetStatus.PENDING_DELETE.name().equals(asset.getStatus())) {
            asset.setStatus(FileAssetStatus.ACTIVE.name());
            asset.setDeleteAfter(null);
            fileAssetDeleteTaskMapper.cancelRebindableTasks(asset.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        if (asset.getActivatedAt() == null) {
            asset.setActivatedAt(now);
        }
        asset.setUpdatedAt(now);
        if (fileAssetMapper.updateById(asset) != 1) {
            throw new BizException(Result.CODE_ERROR, "更新文件资产引用状态失败，请重试");
        }
    }

    /** 中文注释：剔除路径并验证上传文件名，避免将客户端本机目录或超长文本写入数据库。 */
    private String normalizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "上传文件名不能为空");
        }
        String normalized = originalFilename.trim().replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(normalized) || normalized.length() > 255
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "上传文件名不合法");
        }
        return normalized;
    }

    private void insertDeleteTask(FileAsset asset, String reason, LocalDateTime now) {
        FileAssetDeleteTask task = new FileAssetDeleteTask();
        task.setAssetId(asset.getId());
        task.setObjectKey(asset.getObjectKey());
        task.setReason(normalizeReason(reason));
        task.setStatus("WAITING");
        task.setRetryCount(0);
        task.setNextAttemptAt(asset.getDeleteAfter());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (fileAssetDeleteTaskMapper.insert(task) != 1) {
            throw new BizException(Result.CODE_ERROR, "创建文件资产延迟删除任务失败，请重试");
        }
    }

    private Map<Long, FileAsset> lockAssets(Collection<Long> assetIds) {
        Map<Long, FileAsset> locked = new LinkedHashMap<>();
        assetIds.stream().sorted(Comparator.naturalOrder()).forEach(assetId -> {
            FileAsset asset = fileAssetMapper.selectByIdForUpdate(assetId);
            if (asset != null) {
                locked.put(assetId, asset);
            }
        });
        return locked;
    }

    private void validateBindableAsset(FileAsset asset) {
        if (asset == null) {
            return;
        }
        String status = asset.getStatus();
        if (FileAssetStatus.DELETING.name().equals(status) || FileAssetStatus.DELETED.name().equals(status)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "文件资产已进入删除流程，不能重新绑定");
        }
        if (!FileAssetStatus.ACTIVE.name().equals(status)
                && !FileAssetStatus.PENDING_DELETE.name().equals(status)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "文件资产尚未正式启用，不能创建业务引用");
        }
    }

    private void validateBusinessField(String bizType, Long bizId, String fieldKey) {
        if (!StringUtils.hasText(bizType) || bizType.length() > 32 || bizId == null || bizId <= 0
                || !StringUtils.hasText(fieldKey) || fieldKey.length() > 128) {
            throw new BizException(Result.CODE_BAD_REQUEST, "文件资产业务引用参数不合法");
        }
    }

    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "REFERENCE_RELEASED";
        }
        String normalized = reason.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private boolean isExternalUrl(String keyOrUrl) {
        return StringUtils.hasText(keyOrUrl)
                && (keyOrUrl.toLowerCase(Locale.ROOT).startsWith("http://")
                || keyOrUrl.toLowerCase(Locale.ROOT).startsWith("https://"));
    }
}
