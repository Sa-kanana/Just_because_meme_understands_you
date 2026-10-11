package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.FileAssetStatus;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

/** 中文注释：通过 OSS 对象元数据确认临时上传已完成且与票据声明一致。 */
@Service
public class FileAssetUploadConfirmationService {

    /** 中文注释：未绑定业务的临时上传最长确认期限，超过后由临时资产清理任务处理。 */
    private static final long TEMP_ASSET_TTL_DAYS = 3L;

    /** 中文注释：用于读取上传对象元数据并核实直传结果的 OSS 客户端。 */
    @Resource
    private OSS ossClient;

    /** 中文注释：用于查询并更新上传资产状态的持久化映射器。 */
    @Resource
    private FileAssetMapper fileAssetMapper;

    /** 中文注释：存储上传对象的 OSS Bucket 名称配置。 */
    @Value("${oss.bucketName}")
    private String bucketName;

    /**
     * 中文注释：校验资产所有权、状态、有效期和 OSS 元数据，并幂等记录上传确认时间。
     *
     * @param ownerId 当前登录用户ID
     * @param assetId 上传票据返回的文件资产ID
     */
    @Transactional
    public void confirmUpload(Long ownerId, Long assetId) {
        if (ownerId == null || ownerId <= 0 || assetId == null || assetId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "上传确认参数不合法");
        }
        FileAsset asset = fileAssetMapper.selectById(assetId);
        if (asset == null || !ownerId.equals(asset.getOwnerId())) {
            throw new BizException(Result.CODE_FORBIDDEN, "无权确认该上传资产");
        }
        if (!FileAssetStatus.TEMP.name().equals(asset.getStatus())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "上传资产状态不允许确认");
        }
        if (asset.getUploadedAt() != null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (asset.getCreatedAt() == null || asset.getCreatedAt().plusDays(TEMP_ASSET_TTL_DAYS).isBefore(now)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "上传票据已过期，请重新上传");
        }
        if (!StringUtils.hasText(bucketName)
                || !StringUtils.hasText(asset.getScene())
                || !StringUtils.hasText(asset.getObjectKey())
                || !asset.getObjectKey().startsWith("tmp/" + asset.getScene() + "/" + ownerId + "/")
                || asset.getObjectKey().indexOf((char) 92) >= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "上传对象键不合法");
        }
        if (!ossClient.doesObjectExist(bucketName, asset.getObjectKey())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "OSS 中未找到上传文件");
        }

        ObjectMetadata metadata = ossClient.getObjectMetadata(bucketName, asset.getObjectKey());
        if (metadata == null
                || metadata.getContentLength() != asset.getFileSize()
                || !normalizeMimeType(asset.getContentType()).equals(normalizeMimeType(metadata.getContentType()))) {
            throw new BizException(Result.CODE_BAD_REQUEST, "上传文件大小或 MIME 类型与票据不一致");
        }

        asset.setUploadedAt(now);
        asset.setUpdatedAt(now);
        if (fileAssetMapper.updateById(asset) != 1) {
            throw new BizException(Result.CODE_ERROR, "保存上传确认状态失败，请重试");
        }
    }

    /** 中文注释：标准化对象元数据中的 MIME 值以便进行大小写和参数无关的比对。 */
    private String normalizeMimeType(String mimeType) {
        return StringUtils.hasText(mimeType)
                ? mimeType.trim().toLowerCase(Locale.ROOT).split(";", 2)[0].trim()
                : "";
    }
}
