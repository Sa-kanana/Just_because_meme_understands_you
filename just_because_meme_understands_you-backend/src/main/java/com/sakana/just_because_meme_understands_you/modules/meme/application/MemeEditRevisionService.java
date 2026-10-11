package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeEditRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeResourceItemDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeEditRevisionVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.meme.application.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.MemeResourceType;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeEditRevision;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeEditRevisionMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** 中文注释：接收作者修改并保存为独立审核稿，不直接改写正式梗内容。 */
@Service
public class MemeEditRevisionService {

    /** 中文注释：作者编辑时允许的正文名称最大长度。 */
    private static final int MAX_NAME_LENGTH = 50;
    /** 中文注释：作者编辑时允许的正文介绍最大长度。 */
    private static final int MAX_INTRODUCTION_LENGTH = 255;
    /** 中文注释：修改稿允许的媒体资源数量。 */
    private static final int MAX_RESOURCE_URLS = 6;
    /** 中文注释：修改稿允许的结构化外链数量。 */
    private static final int MAX_STRUCTURED_LINKS = 10;
    /** 中文注释：结构化外链标题最大长度。 */
    private static final int MAX_TITLE_LENGTH = 128;
    /** 中文注释：结构化外链地址最大长度。 */
    private static final int MAX_URL_LENGTH = 500;
    /** 中文注释：资源关系中表示有效的状态值。 */
    private static final int RESOURCE_STATUS_ACTIVE = 1;
    /** 中文注释：主动下架梗的资源隐藏状态值。 */
    private static final int RESOURCE_STATUS_INACTIVE = 0;
    /** 中文注释：修改稿待审核状态。 */
    private static final String REVISION_PENDING = "PENDING";

    @Resource
    private MemeMapper memeMapper;

    @Resource
    private MemeEditRevisionMapper memeEditRevisionMapper;

    @Resource
    private MemeResourceMapper memeResourceMapper;

    @Resource
    private MemeTagRelationMapper memeTagRelationMapper;

    @Resource
    private MemeTagMapper memeTagMapper;

    @Resource
    private ObjectMapper objectMapper;

    /** 中文注释：提升编辑稿图片并记录等待审核期间的独立资产引用。 */
    @Resource
    private FileAssetService fileAssetService;

    @Resource
    private IUserProfileService userProfileService;

    /**
     * 中文注释：校验作者权限和正式梗状态后，保存规范化内容快照等待审核。
     */
    @Transactional(rollbackFor = Exception.class)
    public MemeEditRevisionVO submitEdit(Long userId, Long memeId, MemeEditRequestDTO request) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");
        }

        // 中文注释：锁定正式梗行，使并发提交串行检查待审稿且不覆盖管理员状态变更。
        Meme official = memeMapper.selectOne(new LambdaQueryWrapper<Meme>()
                .eq(Meme::getId, memeId)
                .last("FOR UPDATE"));
        if (official == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");
        }
        if (!Objects.equals(official.getUserId(), userId)) {
            throw new BizException(Result.CODE_FORBIDDEN, "无权修改这条梗");
        }
        if (!Objects.equals(official.getStatus(), MemeVisibilitySupport.STATUS_NORMAL)
                && !Objects.equals(official.getStatus(), MemeVisibilitySupport.STATUS_OFFLINE)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "当前状态暂不支持编辑");
        }

        Long pendingCount = memeEditRevisionMapper.selectCount(new LambdaQueryWrapper<MemeEditRevision>()
                .eq(MemeEditRevision::getMemeId, memeId)
                .eq(MemeEditRevision::getStatus, REVISION_PENDING));
        if (pendingCount != null && pendingCount > 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改内容正在审核，请等待审核结果");
        }

        MemeEditRequestDTO normalized = normalizeRequest(userId, request);

        MemeEditRevision revision = new MemeEditRevision();
        revision.setMemeId(memeId);
        revision.setEditorUserId(userId);
        revision.setBaseContentHash(sha256(serialize(buildCurrentSnapshot(official))));
        revision.setContentJson(serialize(normalized));
        revision.setStatus(REVISION_PENDING);
        revision.setCreatedAt(LocalDateTime.now());
        if (memeEditRevisionMapper.insert(revision) != 1 || revision.getId() == null) {
            throw new BizException(Result.CODE_ERROR, "保存梗修改稿失败");
        }
        registerRevisionAssetReferences(revision.getId(), normalized);

        scheduleProfileCacheEviction(userId);

        MemeEditRevisionVO response = new MemeEditRevisionVO();
        response.setRevisionId(revision.getId());
        response.setMemeId(memeId);
        response.setStatus(REVISION_PENDING);
        response.setCreatedAt(revision.getCreatedAt());
        return response;
    }

    private MemeEditRequestDTO normalizeRequest(Long userId, MemeEditRequestDTO request) {
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改内容不能为空");
        }
        String name = request.getName() == null ? "" : request.getName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "梗名称不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BizException(Result.CODE_BAD_REQUEST, "梗名称最多 " + MAX_NAME_LENGTH + " 个字符");
        }
        String introduction = request.getIntroduction() == null ? "" : request.getIntroduction().trim();
        if (introduction.length() > MAX_INTRODUCTION_LENGTH) {
            throw new BizException(Result.CODE_BAD_REQUEST,
                    "梗介绍最多 " + MAX_INTRODUCTION_LENGTH + " 个字符");
        }
        List<Integer> tagIds = normalizeTagIds(request.getTagIds());
        // 中文注释：先确认标签仍存在，再复制 OSS 对象，避免无效请求留下正式目录孤儿文件。
        validateTagsExist(tagIds);
        List<String> mediaUrls = normalizeMediaUrls(request.getResourceUrls());
        List<MemeResourceItemDTO> links = normalizeLinks(request.getResources());

        MemeEditRequestDTO normalized = new MemeEditRequestDTO();
        normalized.setName(name);
        normalized.setIntroduction(introduction);
        String image = request.getImage() == null ? "" : request.getImage().trim();
        String promotedImage = StringUtils.hasText(image) ? fileAssetService.promote(image, userId, "meme") : null;
        normalized.setImage(promotedImage);
        normalized.setTagIds(tagIds);
        normalized.setResourceUrls(mediaUrls.stream()
                .map(url -> url.equals(image) ? promotedImage
                        : fileAssetService.promoteMemeMediaOrKeepExternal(url, userId))
                .toList());
        normalized.setResources(links);
        return normalized;
    }

    /** 中文注释：修改稿按自身 ID 和媒体数组下标持有资产，审核结束后再整体释放或迁移。 */
    private void registerRevisionAssetReferences(Long revisionId, MemeEditRequestDTO revision) {
        if (StringUtils.hasText(revision.getImage())) {
            fileAssetService.replaceReference(revision.getImage(), "MEME_REVISION", revisionId, "COVER",
                    "MEME_REVISION_CREATED");
        }
        List<String> mediaUrls = revision.getResourceUrls();
        for (int index = 0; index < mediaUrls.size(); index++) {
            fileAssetService.replaceReference(mediaUrls.get(index), "MEME_REVISION", revisionId,
                    "RESOURCE:" + index, "MEME_REVISION_CREATED");
        }
    }

    private List<Integer> normalizeTagIds(List<Integer> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Set<Integer> distinct = new TreeSet<>();
        for (Integer tagId : tagIds) {
            if (tagId != null && tagId > 0) {
                distinct.add(tagId);
            }
        }
        return List.copyOf(distinct);
    }

    private List<String> normalizeMediaUrls(List<String> resourceUrls) {
        if (resourceUrls == null || resourceUrls.isEmpty()) {
            return List.of();
        }
        if (resourceUrls.size() > MAX_RESOURCE_URLS) {
            throw new BizException(Result.CODE_BAD_REQUEST, "相关资源最多 " + MAX_RESOURCE_URLS + " 个");
        }
        Set<String> unique = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawUrl : resourceUrls) {
            String url = rawUrl == null ? "" : rawUrl.trim();
            if (!StringUtils.hasText(url)) {
                continue;
            }
            if (url.length() > MAX_URL_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST, "资源地址过长");
            }
            String key = url.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                unique.add(url);
            }
        }
        return List.copyOf(unique);
    }

    private List<MemeResourceItemDTO> normalizeLinks(List<MemeResourceItemDTO> resources) {
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        if (resources.size() > MAX_STRUCTURED_LINKS) {
            throw new BizException(Result.CODE_BAD_REQUEST, "相关链接最多 " + MAX_STRUCTURED_LINKS + " 条");
        }
        Set<String> seenUrls = new LinkedHashSet<>();
        List<MemeResourceItemDTO> normalized = new ArrayList<>();
        for (MemeResourceItemDTO item : resources) {
            if (item == null) {
                continue;
            }
            String url = item.getUrl() == null ? "" : item.getUrl().trim();
            String title = item.getTitle() == null ? "" : item.getTitle().trim();
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
            if (!seenUrls.add(url.toLowerCase(Locale.ROOT))) {
                continue;
            }
            MemeResourceItemDTO copy = new MemeResourceItemDTO();
            copy.setType(MemeResourceType.fromApiType(item.getType()).getApiType());
            copy.setUrl(url);
            copy.setTitle(title);
            copy.setSortOrder(normalized.size());
            normalized.add(copy);
        }
        return List.copyOf(normalized);
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

    private void validateTagsExist(List<Integer> tagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        List<MemeTag> existing = memeTagMapper.selectBatchIds(tagIds);
        Set<Integer> existingIds = new LinkedHashSet<>();
        if (existing != null) {
            for (MemeTag tag : existing) {
                if (tag != null && tag.getId() != null) {
                    existingIds.add(tag.getId());
                }
            }
        }
        for (Integer tagId : tagIds) {
            if (!existingIds.contains(tagId)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "标签不存在, tagId=" + tagId);
            }
        }
    }

    private MemeEditRequestDTO buildCurrentSnapshot(Meme official) {
        MemeEditRequestDTO snapshot = new MemeEditRequestDTO();
        snapshot.setName(official.getName() == null ? "" : official.getName().trim());
        snapshot.setIntroduction(official.getIntroduction() == null ? "" : official.getIntroduction().trim());
        snapshot.setImage(official.getImage() == null ? "" : official.getImage().trim());

        List<MemeTagRelation> relations = memeTagRelationMapper.selectList(new LambdaQueryWrapper<MemeTagRelation>()
                .eq(MemeTagRelation::getMemeId, official.getId())
                .orderByAsc(MemeTagRelation::getMemeTagId));
        List<Integer> tagIds = relations == null ? List.of() : relations.stream()
                .map(MemeTagRelation::getMemeTagId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        snapshot.setTagIds(tagIds);

        int resourceStatus = Objects.equals(official.getStatus(), MemeVisibilitySupport.STATUS_OFFLINE)
                ? RESOURCE_STATUS_INACTIVE : RESOURCE_STATUS_ACTIVE;
        List<MemeResource> resources = memeResourceMapper.selectList(new LambdaQueryWrapper<MemeResource>()
                .eq(MemeResource::getMemeId, official.getId())
                .eq(MemeResource::getStatus, resourceStatus)
                .orderByAsc(MemeResource::getSortOrder)
                .orderByAsc(MemeResource::getId));
        List<String> mediaUrls = new ArrayList<>();
        List<MemeResourceItemDTO> links = new ArrayList<>();
        if (resources != null) {
            for (MemeResource resource : resources) {
                if (resource == null || !StringUtils.hasText(resource.getResourceUrl())) {
                    continue;
                }
                MemeResourceType type = MemeResourceType.fromCode(resource.getResourceType());
                if (type == MemeResourceType.MEDIA) {
                    mediaUrls.add(resource.getResourceUrl().trim());
                } else {
                    MemeResourceItemDTO item = new MemeResourceItemDTO();
                    item.setType(type.getApiType());
                    item.setUrl(resource.getResourceUrl().trim());
                    item.setTitle(resource.getTitle() == null ? "" : resource.getTitle().trim());
                    item.setSortOrder(links.size());
                    links.add(item);
                }
            }
        }
        snapshot.setResourceUrls(mediaUrls);
        snapshot.setResources(links);
        return snapshot;
    }

    private String serialize(MemeEditRequestDTO snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new BizException(Result.CODE_ERROR, "修改内容保存失败");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private void scheduleProfileCacheEviction(Long userId) {
        Runnable eviction = () -> userProfileService.evictUserMemesCache(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }
}
