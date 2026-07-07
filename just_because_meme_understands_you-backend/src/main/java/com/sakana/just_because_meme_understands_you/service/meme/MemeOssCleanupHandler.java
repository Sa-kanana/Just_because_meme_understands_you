package com.sakana.just_because_meme_understands_you.service.meme;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 梗彻底删除后异步清理 OSS 对象，失败仅记录日志，不阻塞主流程。
 */
@Slf4j
@Service
public class MemeOssCleanupHandler {

    @Value("${oss.bucketName:}")
    private String bucketName;

    @Resource
    private OSS ossClient;

    @Resource
    private MemeResourceMapper memeResourceMapper;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Async("memePublishExecutor")
    public void cleanupMemeAssets(Meme meme) {
        if (meme == null || !StringUtils.hasText(bucketName)) {
            return;
        }
        Set<String> objectKeys = new HashSet<>();
        collectOwnedObjectKey(meme.getImage(), objectKeys);

        List<MemeResource> resources = memeResourceMapper.selectList(
                new LambdaQueryWrapper<MemeResource>()
                        .eq(MemeResource::getMemeId, meme.getId() != null ? meme.getId().longValue() : null)
        );
        if (resources != null) {
            for (MemeResource resource : resources) {
                collectOwnedObjectKey(resource.getResourceUrl(), objectKeys);
            }
        }

        for (String key : objectKeys) {
            try {
                ossClient.deleteObject(bucketName, key);
            } catch (Exception e) {
                log.warn("OSS 对象清理失败, memeId={}, key={}", meme.getId(), key, e);
            }
        }
    }

    private void collectOwnedObjectKey(String stored, Set<String> objectKeys) {
        if (!StringUtils.hasText(stored)) {
            return;
        }
        String normalized = ossUrlHelper.normalizeForStorage(stored);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return;
        }
        objectKeys.add(normalized);
    }
}

