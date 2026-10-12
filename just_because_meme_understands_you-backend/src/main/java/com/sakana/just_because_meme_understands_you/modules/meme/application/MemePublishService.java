package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.MemeResourceType;
import com.sakana.just_because_meme_understands_you.modules.meme.application.support.MemeTagSelectionPolicy;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeResourceItemDTO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService;
import com.sakana.just_because_meme_understands_you.modules.moderation.application.SensitiveWordFilterService;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveScene;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCreateResponseVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 梗发布服务：在单一事务内写入 meme 主表、meme_resource 资源表、meme_tag_relation 关联表，
 * 事务提交后再异步累加标签计数并将梗 id 加入布隆过滤器。
 *
 * @author sakana
 */
@Slf4j
@Service
public class MemePublishService {

    /** 新发布梗默认状态：2=审核中 */
    private static final int DEFAULT_STATUS = 2;
    private static final String DEFAULT_STATUS_DESC = "审核中";
    private static final int MAX_RESOURCE_URLS = 6;
    private static final int MAX_STRUCTURED_LINKS = 10;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_URL_LENGTH = 500;
    private static final int RESOURCE_STATUS_ACTIVE = 1;

    @Resource
    private MemeMapper memeMapper;

    @Resource
    private MemeResourceMapper memeResourceMapper;

    @Resource
    private MemeTagRelationMapper memeTagRelationMapper;

    @Resource
    private MemeTagMapper memeTagMapper;

    @Resource
    private MemeBloomFilterService memeBloomFilterService;

    @Resource
    private IUserProfileService userProfileService;

    /** 中文注释：提升梗图片并维护封面及资源行对应的文件资产引用。 */
    @Resource
    private FileAssetService fileAssetService;

    /** 中文注释：发布中的 REVIEW 命中进入当前新梗审核队列，BLOCK 命中在写库和 OSS 操作前拒绝。 */
    @Resource
    private SensitiveWordFilterService sensitiveWordFilterService;

    /**
     * 发布梗。
     *
     * @param userId  发布者 id
     * @param request 发布请求
     * @return 新梗 id 与状态
     */
    @Transactional(rollbackFor = Exception.class)
    public MemeCreateResponseVO publish(Long userId, MemeCreateRequestDTO request) {
        validate(userId, request);
        filterReviewableText(request);

        MemeTagSelectionPolicy.Selection tagSelection = validateTagSelection(request.getPrimaryTagId(), request.getTagIds());
        List<Integer> tagIds = tagSelection.allTagIds();

        LocalDateTime now = LocalDateTime.now();

        Map<String, String> promotedMedia = new HashMap<>();
        String imageKey = null;
        if (StringUtils.hasText(request.getImage())) {
            String submittedImage = request.getImage().trim();
            imageKey = fileAssetService.promote(submittedImage, userId, "meme");
            promotedMedia.put(submittedImage, imageKey);
        }

        // 1. 写入 meme 主表
        Meme meme = new Meme();
        meme.setName(request.getName().trim());
        String introduction = request.getIntroduction() == null ? "" : request.getIntroduction().trim();
        meme.setIntroduction(introduction);
        meme.setImage(imageKey);
        meme.setPageViews(0);
        meme.setLikes(0);
        meme.setComments(0);
        meme.setStatus(DEFAULT_STATUS);
        meme.setReleaseTime(now);
        meme.setUpdateTime(now);
        meme.setUserId(userId);
        memeMapper.insert(meme);
        Long memeId = meme.getId();
        if (memeId == null) {
            throw new BizException(Result.CODE_ERROR, "梗主表写入失败");
        }
        fileAssetService.replaceReference(imageKey, "MEME", memeId, "COVER", "MEME_PUBLISHED");

        // 2. 写入 meme_resource 资源表
        saveMediaResources(userId, memeId, request.getResourceUrls(), promotedMedia);
        saveStructuredLinks(memeId, request.getResources());

        // 3. 写入 meme_tag_relation 关联表
        saveTagRelations(memeId, tagSelection);

        // 4. 非核心：标签计数 + 布隆热加载 + 缓存清理，事务提交后异步执行
        scheduleAfterPublishCommitted(userId, memeId, tagIds);

        MemeCreateResponseVO vo = new MemeCreateResponseVO();
        vo.setMemeId(memeId);
        vo.setStatus(DEFAULT_STATUS);
        vo.setStatusDesc(DEFAULT_STATUS_DESC);
        return vo;
    }

    /** 中文注释：在上传或持久化前统一检查新梗文字字段，并保存 NORMAL 等级的替换结果。 */
    private void filterReviewableText(MemeCreateRequestDTO request) {
        String name = sensitiveWordFilterService.filterForReviewableContent(
                request.getName().trim(), SensitiveScene.MEME_TITLE);
        if (name.length() > 255) {
            throw new BizException(Result.CODE_BAD_REQUEST, "梗名称最多 255 个字符");
        }
        request.setName(name);
        String introduction = request.getIntroduction() == null ? "" : request.getIntroduction().trim();
        introduction = sensitiveWordFilterService.filterForReviewableContent(
                introduction, SensitiveScene.MEME_DESCRIPTION);
        if (introduction.length() > 255) {
            throw new BizException(Result.CODE_BAD_REQUEST, "梗介绍最多 255 个字符");
        }
        request.setIntroduction(introduction);
        if (request.getResources() != null) {
            request.getResources().stream().filter(java.util.Objects::nonNull)
                    .filter(resource -> StringUtils.hasText(resource.getTitle()))
                    .forEach(resource -> {
                        String title = sensitiveWordFilterService.filterForReviewableContent(
                                resource.getTitle().trim(), SensitiveScene.MEME_RESOURCE_TITLE);
                        if (title.length() > MAX_TITLE_LENGTH) {
                            throw new BizException(Result.CODE_BAD_REQUEST,
                                    "相关链接标题最多 " + MAX_TITLE_LENGTH + " 个字符");
                        }
                        resource.setTitle(title);
                    });
        }
    }

    private void validate(Long userId, MemeCreateRequestDTO request) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "梗名称不能为空");
        }
    }

    /** 中文注释：将主标签和普通标签合并查询，统一执行数量、唯一性及标签存在性校验。 */
    private MemeTagSelectionPolicy.Selection validateTagSelection(Integer primaryTagId, List<Integer> secondaryTagIds) {
        Set<Integer> candidateIds = new HashSet<>();
        if (primaryTagId != null && primaryTagId > 0) {
            candidateIds.add(primaryTagId);
        }
        if (secondaryTagIds != null) {
            secondaryTagIds.stream().filter(id -> id != null && id > 0).forEach(candidateIds::add);
        }
        List<MemeTag> existingTags = candidateIds.isEmpty() ? List.of() : memeTagMapper.selectBatchIds(candidateIds);
        Set<Integer> existingIds = existingTags == null ? Set.of() : existingTags.stream()
                .filter(java.util.Objects::nonNull).map(MemeTag::getId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return MemeTagSelectionPolicy.normalizeAndValidate(primaryTagId, secondaryTagIds, existingIds);
    }

    private void saveMediaResources(Long userId, Long memeId, List<String> resourceUrls,
                                    Map<String, String> promotedMedia) {
        if (resourceUrls == null || resourceUrls.isEmpty()) {
            return;
        }
        if (resourceUrls.size() > MAX_RESOURCE_URLS) {
            throw new BizException(Result.CODE_BAD_REQUEST, "相关资源最多 " + MAX_RESOURCE_URLS + " 个");
        }
        LocalDateTime now = LocalDateTime.now();
        int sortOrder = 0;
        for (String url : resourceUrls) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            String normalized = promotedMedia.computeIfAbsent(url.trim(),
                    submittedUrl -> fileAssetService.promoteMemeMediaOrKeepExternal(submittedUrl, userId));
            MemeResource resource = new MemeResource();
            resource.setMemeId(memeId);
            resource.setResourceUrl(normalized);
            resource.setResourceType(MemeResourceType.MEDIA.getCode());
            resource.setSortOrder(sortOrder++);
            resource.setStatus(RESOURCE_STATUS_ACTIVE);
            resource.setCreateTime(now);
            if (memeResourceMapper.insert(resource) != 1 || resource.getId() == null) {
                throw new BizException(Result.CODE_ERROR, "保存梗图片资源失败");
            }
            fileAssetService.replaceReference(normalized, "MEME_RESOURCE", resource.getId(), "URL",
                    "MEME_RESOURCE_CREATED");
        }
    }

    private void saveStructuredLinks(Long memeId, List<MemeResourceItemDTO> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        if (resources.size() > MAX_STRUCTURED_LINKS) {
            throw new BizException(Result.CODE_BAD_REQUEST, "相关链接最多 " + MAX_STRUCTURED_LINKS + " 条");
        }
        LocalDateTime now = LocalDateTime.now();
        Set<String> seenUrls = new HashSet<>();
        List<MemeResourceItemDTO> validItems = new ArrayList<>();
        for (MemeResourceItemDTO item : resources) {
            if (item == null) {
                continue;
            }
            String url = item.getUrl() != null ? item.getUrl().trim() : "";
            String title = item.getTitle() != null ? item.getTitle().trim() : "";
            if (!StringUtils.hasText(url) && !StringUtils.hasText(title)) {
                continue;
            }
            if (!StringUtils.hasText(url)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "相关链接 URL 不能为空");
            }
            if (!StringUtils.hasText(title)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "相关链接标题不能为空");
            }
            if (title.length() > MAX_TITLE_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST, "相关链接标题最多 " + MAX_TITLE_LENGTH + " 个字符");
            }
            assertSafeExternalUrl(url);
            String dedupeKey = url.toLowerCase();
            if (!seenUrls.add(dedupeKey)) {
                continue;
            }
            validItems.add(item);
        }
        for (MemeResourceItemDTO item : validItems) {
            MemeResourceType type = MemeResourceType.fromApiType(item.getType());
            int sortOrder = item.getSortOrder() != null ? item.getSortOrder() : 0;
            MemeResource resource = new MemeResource();
            resource.setMemeId(memeId);
            resource.setResourceUrl(item.getUrl().trim());
            resource.setResourceType(type.getCode());
            resource.setTitle(item.getTitle().trim());
            resource.setSortOrder(sortOrder);
            resource.setStatus(RESOURCE_STATUS_ACTIVE);
            resource.setCreateTime(now);
            memeResourceMapper.insert(resource);
        }
    }

    private void assertSafeExternalUrl(String url) {
        if (url.length() > MAX_URL_LENGTH) {
            throw new BizException(Result.CODE_BAD_REQUEST, "链接地址过长");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, "链接地址格式不合法");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new BizException(Result.CODE_BAD_REQUEST, "链接须以 http:// 或 https:// 开头");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "链接地址格式不合法");
        }
    }

    private void saveTagRelations(Long memeId, MemeTagSelectionPolicy.Selection selection) {
        for (Integer tagId : selection.allTagIds()) {
            MemeTagRelation relation = new MemeTagRelation();
            relation.setMemeId(memeId);
            relation.setMemeTagId(tagId);
            boolean isPrimary = tagId.equals(selection.primaryTagId());
            relation.setIsPrimary(isPrimary ? 1 : 0);
            relation.setPrimaryMemeId(isPrimary ? memeId : null);
            memeTagRelationMapper.insert(relation);
        }
    }

    /**
     * 事务提交后再触发异步链路，避免主事务回滚却已累加计数 / 加入布隆。
     */
    private void scheduleAfterPublishCommitted(Long userId, Long memeId, List<Integer> tagIds) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runAfterPublish(userId, memeId, tagIds);
                }
            });
        } else {
            runAfterPublish(userId, memeId, tagIds);
        }
    }

    private void runAfterPublish(Long userId, Long memeId, List<Integer> tagIds) {
        // 布隆过滤器热加载：同步追加，保证后续评论/收藏不被误拦
        try {
            memeBloomFilterService.add(memeId);
        } catch (Exception e) {
            log.warn("布隆过滤器追加失败, memeId={}", memeId, e);
        }
        // 标签 related_quantity 仅在审核通过（2→1）时累加，发布进审不加
        // 清理该用户发布列表缓存，保证前端刷新看到最新数据
        try {
            userProfileService.evictUserMemesCache(userId);
        } catch (Exception e) {
            log.warn("清理用户发布列表缓存失败, userId={}", userId, e);
        }
    }

    /**
     * 供评论/收藏入口做快速存在性预判。
     */
    public boolean mightExist(Long memeId) {
        return memeBloomFilterService.mightContain(memeId);
    }
}
