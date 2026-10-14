package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeEditRevisionVO;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeEditRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeResourceItemDTO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.pagination.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.transaction.AfterCommitExecutor;
import com.sakana.just_because_meme_understands_you.modules.meme.application.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeIndexEventPort;
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
import com.sakana.just_because_meme_understands_you.modules.notification.application.NotificationPublisher;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** 中文注释：管理端修改稿审核流程，审核通过后原子替换正式内容。 */
@Slf4j
@Service
public class MemeEditRevisionReviewService {

    /** 中文注释：待审核修改稿状态。 */
    private static final String PENDING = "PENDING";
    /** 中文注释：审核通过状态。 */
    private static final String APPROVED = "APPROVED";
    /** 中文注释：审核驳回状态。 */
    private static final String REJECTED = "REJECTED";
    /** 中文注释：因正式梗状态变化而取消的修改稿状态。 */
    private static final String CANCELED = "CANCELED";
    /** 中文注释：有效梗资源状态。 */
    private static final int RESOURCE_ACTIVE = 1;
    /** 中文注释：已停用梗资源状态。 */
    private static final int RESOURCE_INACTIVE = 0;
    /** 中文注释：已被审核通过的新内容替换、只保留归档的资源状态。 */
    private static final int RESOURCE_REPLACED = 2;
    /** 中文注释：外链标题最大长度。 */
    private static final int MAX_TITLE_LENGTH = 128;
    /** 中文注释：资源地址最大长度。 */
    private static final int MAX_URL_LENGTH = 500;

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

    @Resource
    private NotificationPublisher notificationPublisher;

    /** 中文注释：审核时将修改稿图片引用转移到正式梗和资源行。 */
    @Resource
    private FileAssetService fileAssetService;

    @Resource
    private IUserProfileService userProfileService;

    @Resource
    private IMemeService memeService;

    @Resource
    private MemePublishAsyncHandler memePublishAsyncHandler;

    @Resource
    private MemeIndexEventPort memeIndexEventPort;

    @Resource
    private AfterCommitExecutor afterCommitExecutor;

    /** 中文注释：分页查询等待管理员处理的修改稿摘要。 */
    public PageVO<AdminMemeEditRevisionVO> pagePending(Integer page, Integer size) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);
        Page<MemeEditRevision> source = memeEditRevisionMapper.selectPage(
                new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MemeEditRevision>()
                        .eq(MemeEditRevision::getStatus, PENDING)
                        .orderByAsc(MemeEditRevision::getCreatedAt)
                        .orderByAsc(MemeEditRevision::getId));
        List<MemeEditRevision> revisions = source.getRecords() == null ? List.of() : source.getRecords();
        List<Long> memeIds = revisions.stream().map(MemeEditRevision::getMemeId)
                .filter(Objects::nonNull).distinct().toList();
        List<Meme> memes = memeIds.isEmpty() ? List.of() : memeMapper.selectBatchIds(memeIds);
        java.util.Map<Long, Meme> memeById = new java.util.HashMap<>();
        if (memes != null) {
            for (Meme meme : memes) {
                if (meme != null && meme.getId() != null) {
                    memeById.put(meme.getId(), meme);
                }
            }
        }
        PageVO<AdminMemeEditRevisionVO> result = new PageVO<>();
        result.setList(revisions.stream().map(revision -> toView(revision, memeById.get(revision.getMemeId()), false))
                .toList());
        result.setPage(pageNo);
        result.setSize(pageSize);
        result.setTotal(source.getTotal());
        result.setHasMore((long) pageNo * pageSize < source.getTotal());
        return result;
    }

    /** 中文注释：读取原始正式版本和修改稿快照，供管理员逐项对比。 */
    public AdminMemeEditRevisionVO detail(Long revisionId) {
        MemeEditRevision revision = requireRevision(revisionId, false);
        Meme official = revision.getMemeId() == null ? null : memeMapper.selectById(revision.getMemeId());
        return toView(revision, official, true);
    }

    /** 中文注释：校验内容基线后替换正式内容，并保留梗状态、发布时间和互动计数。 */
    @Transactional(rollbackFor = Exception.class)
    public AdminMemeEditRevisionVO approve(Long revisionId, Long adminUserId) {
        requireAdminId(adminUserId);
        MemeEditRevision revision = requireRevision(revisionId, true);
        requirePending(revision);
        Meme official = lockMeme(revision.getMemeId());
        if (official == null) {
            return cancelRevision(revision, null, adminUserId, "梗已不存在，修改稿已取消");
        }
        if (!isEditableStatus(official.getStatus())) {
            String note = Objects.equals(official.getStatus(), MemeVisibilitySupport.STATUS_LOCKED)
                    ? "梗已被锁定，修改稿已取消" : "梗状态已变化，修改稿已取消";
            return cancelRevision(revision, official, adminUserId, note);
        }

        MemeEditRequestDTO currentContent = buildCurrentSnapshot(official);
        String currentHash = sha256(serialize(currentContent));
        String legacyCurrentHash = legacySnapshotHash(currentContent);
        MemeEditRequestDTO submitted = parseSubmittedContent(revision.getContentJson());
        boolean legacyPendingRevision = submitted.getPrimaryTagId() == null;
        if (!Objects.equals(revision.getBaseContentHash(), currentHash)
                && !(legacyPendingRevision && Objects.equals(revision.getBaseContentHash(), legacyCurrentHash))) {
            throw new BizException(Result.CODE_CONFLICT, "正式内容已变化，请刷新审核详情后重新处理");
        }

        validateSubmittedContent(submitted);
        List<Integer> oldTagIds = allTagIds(currentContent);
        List<Integer> newTagIds = allTagIds(submitted);
        List<MemeResource> previousResources = memeResourceMapper.selectList(new LambdaQueryWrapper<MemeResource>()
                .eq(MemeResource::getMemeId, official.getId()));

        LocalDateTime now = LocalDateTime.now();
        memeMapper.update(null, new UpdateWrapper<Meme>()
                .eq("id", official.getId())
                .set("name", submitted.getName())
                .set("introduction", submitted.getIntroduction())
                .set("image", submitted.getImage())
                .set("update_time", now));
        if (submitted.getImage() == null) {
            fileAssetService.releaseReference("MEME", official.getId(), "COVER", "MEME_REVISION_APPROVED");
        } else {
            fileAssetService.replaceReference(submitted.getImage(), "MEME", official.getId(), "COVER",
                    "MEME_REVISION_APPROVED");
        }
        replaceTagRelations(official.getId(), submitted);
        replaceResources(official.getId(), submitted, now, previousResources);
        releaseRevisionAssetReferences(revision.getId(), submitted, "MEME_REVISION_APPROVED");
        updateTagCounters(official.getStatus(), oldTagIds, newTagIds);

        revision.setStatus(APPROVED);
        revision.setReviewedBy(adminUserId);
        revision.setReviewedAt(now);
        revision.setReviewNote(null);
        memeEditRevisionMapper.updateById(revision);
        notificationPublisher.publishMemeEditReview(revision.getEditorUserId(), adminUserId,
                revision.getId(), official.getId(), submitted.getName(), submitted.getImage(), APPROVED, null);
        memeIndexEventPort.requestUpsert(official.getId());
        scheduleAfterCommitEffects(official.getUserId(), official.getId());
        official.setName(submitted.getName())
                .setIntroduction(submitted.getIntroduction())
                .setImage(submitted.getImage())
                .setUpdateTime(now);
        return toView(revision, official, true, submitted);
    }

    /** 中文注释：驳回修改稿并保留正式内容，要求作者看到明确原因。 */
    @Transactional(rollbackFor = Exception.class)
    public AdminMemeEditRevisionVO reject(Long revisionId, Long adminUserId, String note) {
        requireAdminId(adminUserId);
        String normalizedNote = note == null ? "" : note.trim();
        if (!StringUtils.hasText(normalizedNote)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "驳回修改稿时必须填写原因");
        }
        if (normalizedNote.length() > 500) {
            throw new BizException(Result.CODE_BAD_REQUEST, "审核说明最多500个字符");
        }
        MemeEditRevision revision = requireRevision(revisionId, true);
        requirePending(revision);
        Meme official = lockMeme(revision.getMemeId());
        MemeEditRequestDTO submitted = parseSubmittedContent(revision.getContentJson());

        LocalDateTime now = LocalDateTime.now();
        revision.setStatus(REJECTED);
        revision.setReviewedBy(adminUserId);
        revision.setReviewedAt(now);
        revision.setReviewNote(normalizedNote);
        memeEditRevisionMapper.updateById(revision);
        releaseRevisionAssetReferences(revision.getId(), submitted, "MEME_REVISION_REJECTED");
        notificationPublisher.publishMemeEditReview(revision.getEditorUserId(), adminUserId,
                revision.getId(), revision.getMemeId(), official == null ? null : official.getName(),
                official == null ? null : official.getImage(), REJECTED, normalizedNote);
        scheduleAfterCommitEffects(revision.getEditorUserId(), revision.getMemeId());
        return toView(revision, official, true, submitted);
    }

    private MemeEditRevision requireRevision(Long revisionId, boolean forUpdate) {
        if (revisionId == null || revisionId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改稿ID不合法");
        }
        LambdaQueryWrapper<MemeEditRevision> wrapper = new LambdaQueryWrapper<MemeEditRevision>()
                .eq(MemeEditRevision::getId, revisionId);
        if (forUpdate) {
            wrapper.last("FOR UPDATE");
        }
        MemeEditRevision revision = memeEditRevisionMapper.selectOne(wrapper);
        if (revision == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗修改稿不存在");
        }
        return revision;
    }

    private Meme lockMeme(Long memeId) {
        if (memeId == null || memeId <= 0) {
            return null;
        }
        return memeMapper.selectOne(new LambdaQueryWrapper<Meme>()
                .eq(Meme::getId, memeId)
                .last("FOR UPDATE"));
    }

    private void requirePending(MemeEditRevision revision) {
        if (!PENDING.equals(revision.getStatus())) {
            throw new BizException(Result.CODE_CONFLICT, "该修改稿已处理，请刷新列表");
        }
    }

    private AdminMemeEditRevisionVO cancelRevision(MemeEditRevision revision, Meme official,
                                                    Long adminUserId, String note) {
        LocalDateTime now = LocalDateTime.now();
        revision.setStatus(CANCELED);
        revision.setReviewedBy(adminUserId);
        revision.setReviewedAt(now);
        revision.setReviewNote(note);
        memeEditRevisionMapper.updateById(revision);
        notificationPublisher.publishMemeEditReview(revision.getEditorUserId(), adminUserId,
                revision.getId(), revision.getMemeId(), official == null ? null : official.getName(),
                official == null ? null : official.getImage(), CANCELED, note);
        MemeEditRequestDTO submitted = parseSubmittedContent(revision.getContentJson());
        releaseRevisionAssetReferences(revision.getId(), submitted, "MEME_REVISION_CANCELED");
        scheduleAfterCommitEffects(revision.getEditorUserId(), revision.getMemeId());
        return toView(revision, official, true, submitted);
    }

    private void replaceTagRelations(Long memeId, MemeEditRequestDTO content) {
        memeTagRelationMapper.delete(new LambdaQueryWrapper<MemeTagRelation>()
                .eq(MemeTagRelation::getMemeId, memeId));
        for (Integer tagId : allTagIds(content)) {
            boolean isPrimary = Objects.equals(tagId, content.getPrimaryTagId());
            memeTagRelationMapper.insert(new MemeTagRelation().setMemeId(memeId).setMemeTagId(tagId)
                    .setIsPrimary(isPrimary ? 1 : 0)
                    .setPrimaryMemeId(isPrimary ? memeId : null));
        }
    }

    private void replaceResources(Long memeId, MemeEditRequestDTO content, LocalDateTime now,
                                  List<MemeResource> previousResources) {
        memeResourceMapper.update(null, new UpdateWrapper<MemeResource>()
                .eq("meme_id", memeId)
                .set("status", RESOURCE_REPLACED));
        int order = 0;
        for (String url : content.getResourceUrls()) {
            MemeResource resource = new MemeResource().setMemeId(memeId).setResourceUrl(url)
                    .setResourceType(MemeResourceType.MEDIA.getCode()).setTitle(null)
                    .setSortOrder(order++).setStatus(RESOURCE_ACTIVE).setCreateTime(now);
            insertAndTrackResource(resource, "MEME_REVISION_APPROVED");
        }
        for (MemeResourceItemDTO link : content.getResources()) {
            MemeResource resource = new MemeResource().setMemeId(memeId).setResourceUrl(link.getUrl())
                    .setResourceType(MemeResourceType.fromApiType(link.getType()).getCode())
                    .setTitle(link.getTitle()).setSortOrder(order++).setStatus(RESOURCE_ACTIVE).setCreateTime(now);
            insertAndTrackResource(resource, "MEME_REVISION_APPROVED");
        }
        if (previousResources != null) {
            for (MemeResource previous : previousResources) {
                if (previous != null && previous.getId() != null) {
                    fileAssetService.releaseReference("MEME_RESOURCE", previous.getId(), "URL",
                            "MEME_RESOURCES_REPLACED");
                }
            }
        }
    }

    /** 中文注释：保存正式资源行后使用数据库生成的主键绑定资源图片。 */
    private void insertAndTrackResource(MemeResource resource, String reason) {
        if (memeResourceMapper.insert(resource) != 1 || resource.getId() == null) {
            throw new BizException(Result.CODE_ERROR, "保存梗资源失败，请重试");
        }
        fileAssetService.replaceReference(resource.getResourceUrl(), "MEME_RESOURCE", resource.getId(), "URL", reason);
    }

    /** 中文注释：正式内容接管后释放编辑稿封面、媒体和站内结构化链接的引用。 */
    private void releaseRevisionAssetReferences(Long revisionId, MemeEditRequestDTO content, String reason) {
        if (content == null) {
            return;
        }
        if (StringUtils.hasText(content.getImage())) {
            fileAssetService.releaseReference("MEME_REVISION", revisionId, "COVER", reason);
        }
        List<String> mediaUrls = content.getResourceUrls();
        for (int index = 0; mediaUrls != null && index < mediaUrls.size(); index++) {
            fileAssetService.releaseReference("MEME_REVISION", revisionId, "RESOURCE:" + index, reason);
        }
        List<MemeResourceItemDTO> links = content.getResources();
        for (int index = 0; links != null && index < links.size(); index++) {
            fileAssetService.releaseReference("MEME_REVISION", revisionId, "LINK:" + index, reason);
        }
    }

    private void updateTagCounters(Integer memeStatus, List<Integer> oldTagIds, List<Integer> newTagIds) {
        if (!Objects.equals(memeStatus, MemeVisibilitySupport.STATUS_NORMAL)) {
            return;
        }
        List<Integer> removed = differenceTagIds(oldTagIds, newTagIds);
        List<Integer> added = differenceTagIds(newTagIds, oldTagIds);
        if (removed.isEmpty() && added.isEmpty()) {
            return;
        }
        afterCommitExecutor.execute(() -> {
            memePublishAsyncHandler.decrementTagRelatedQuantity(removed);
            memePublishAsyncHandler.incrementTagRelatedQuantity(added);
        });
    }

    private MemeEditRequestDTO parseSubmittedContent(String contentJson) {
        try {
            MemeEditRequestDTO content = objectMapper.readValue(contentJson, MemeEditRequestDTO.class);
            if (content.getTagIds() == null) {
                content.setTagIds(List.of());
            }
            if (content.getResourceUrls() == null) {
                content.setResourceUrls(List.of());
            }
            if (content.getResources() == null) {
                content.setResources(List.of());
            }
            return content;
        } catch (Exception exception) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改稿内容格式错误，无法审核");
        }
    }

    private void validateSubmittedContent(MemeEditRequestDTO content) {
        if (content == null || !StringUtils.hasText(content.getName()) || content.getName().length() > 50
                || (content.getIntroduction() != null && content.getIntroduction().length() > 255)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改稿正文格式不合法");
        }
        if (content.getResourceUrls().size() > 6 || content.getResources().size() > 10) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改稿资源数量超出限制");
        }
        if (content.getTagIds().size() > 7) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改稿普通标签最多选择 7 个");
        }
        Set<Integer> secondaryTagIds = new TreeSet<>();
        for (Integer tagId : content.getTagIds()) {
            if (tagId == null || tagId <= 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, "修改稿标签不合法");
            }
            if (!secondaryTagIds.add(tagId)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "修改稿标签不能重复");
            }
        }
        Set<Integer> submittedTagIds = new TreeSet<>(secondaryTagIds);
        if (content.getPrimaryTagId() != null) {
            if (content.getPrimaryTagId() <= 0 || secondaryTagIds.contains(content.getPrimaryTagId())) {
                throw new BizException(Result.CODE_BAD_REQUEST, "修改稿主标签不合法或重复");
            }
            submittedTagIds.add(content.getPrimaryTagId());
        }
        List<MemeTag> tags = submittedTagIds.isEmpty() ? List.of() : memeTagMapper.selectBatchIds(submittedTagIds);
        Set<Integer> existingIds = tags == null ? Set.of()
                : tags.stream().filter(Objects::nonNull).map(MemeTag::getId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!existingIds.containsAll(submittedTagIds)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改稿包含已失效标签");
        }
        if (content.getPrimaryTagId() == null && !secondaryTagIds.isEmpty()) {
            // 中文注释：兼容旧版待审稿，其全部标签都存放在 tagIds 中，审核时取最小 ID 作为主标签。
            content.setPrimaryTagId(secondaryTagIds.iterator().next());
            secondaryTagIds.remove(content.getPrimaryTagId());
        }
        content.setTagIds(List.copyOf(secondaryTagIds));
        String image = StringUtils.hasText(content.getImage()) ? content.getImage().trim() : null;
        content.setImage(image);
        if (image != null && (image.length() > MAX_URL_LENGTH || isHttpUrl(image))) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改稿封面地址不合法");
        }
        for (String url : content.getResourceUrls()) {
            if (!StringUtils.hasText(url) || url.length() > MAX_URL_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST, "修改稿媒体资源地址不合法");
            }
        }
        for (MemeResourceItemDTO link : content.getResources()) {
            if (link == null || !StringUtils.hasText(link.getTitle())
                    || link.getTitle().length() > MAX_TITLE_LENGTH
                    || !StringUtils.hasText(link.getUrl()) || link.getUrl().length() > MAX_URL_LENGTH) {
                throw new BizException(Result.CODE_BAD_REQUEST, "修改稿外链信息不合法");
            }
            assertSafeExternalUrl(link.getUrl());
        }
    }

    private void assertSafeExternalUrl(String url) {
        try {
            URI parsed = URI.create(url);
            String scheme = parsed.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || !StringUtils.hasText(parsed.getHost())) {
                throw new IllegalArgumentException("unsafe URL");
            }
        } catch (Exception exception) {
            throw new BizException(Result.CODE_BAD_REQUEST, "修改稿外链须为有效的 HTTP 或 HTTPS 地址");
        }
    }

    private boolean isHttpUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }

    private MemeEditRequestDTO buildCurrentSnapshot(Meme official) {
        MemeEditRequestDTO snapshot = new MemeEditRequestDTO();
        snapshot.setName(official.getName() == null ? "" : official.getName().trim());
        snapshot.setIntroduction(official.getIntroduction() == null ? "" : official.getIntroduction().trim());
        snapshot.setImage(official.getImage() == null ? "" : official.getImage().trim());
        List<MemeTagRelation> relations = memeTagRelationMapper.selectList(new LambdaQueryWrapper<MemeTagRelation>()
                .eq(MemeTagRelation::getMemeId, official.getId())
                .orderByAsc(MemeTagRelation::getMemeTagId));
        List<MemeTagRelation> validRelations = relations == null ? List.of() : relations.stream()
                .filter(Objects::nonNull).filter(relation -> relation.getMemeTagId() != null).toList();
        Integer primaryTagId = validRelations.stream()
                .filter(relation -> Objects.equals(relation.getIsPrimary(), 1))
                .map(MemeTagRelation::getMemeTagId).findFirst().orElse(null);
        snapshot.setPrimaryTagId(primaryTagId);
        snapshot.setTagIds(validRelations.stream().map(MemeTagRelation::getMemeTagId)
                .filter(id -> !Objects.equals(id, primaryTagId)).distinct().sorted().toList());
        int resourceStatus = Objects.equals(official.getStatus(), MemeVisibilitySupport.STATUS_OFFLINE)
                ? RESOURCE_INACTIVE : RESOURCE_ACTIVE;
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

    private AdminMemeEditRevisionVO toView(MemeEditRevision revision, Meme official, boolean includeCurrent) {
        return toView(revision, official, includeCurrent, parseSubmittedContent(revision.getContentJson()));
    }

    private AdminMemeEditRevisionVO toView(MemeEditRevision revision, Meme official, boolean includeCurrent,
                                            MemeEditRequestDTO submitted) {
        AdminMemeEditRevisionVO view = new AdminMemeEditRevisionVO();
        view.setRevisionId(revision.getId());
        view.setMemeId(revision.getMemeId());
        view.setEditorUserId(revision.getEditorUserId());
        view.setMemeName(official == null ? null : official.getName());
        view.setMemeStatus(official == null ? null : official.getStatus());
        view.setStatus(revision.getStatus());
        view.setReviewNote(revision.getReviewNote());
        view.setCreatedAt(revision.getCreatedAt());
        view.setReviewedAt(revision.getReviewedAt());
        view.setSubmittedContent(submitted);
        if (includeCurrent && official != null) {
            view.setCurrentContent(buildCurrentSnapshot(official));
        }
        return view;
    }

    private List<Integer> differenceTagIds(Collection<Integer> left, Collection<Integer> right) {
        Set<Integer> rightSet = right == null ? Set.of() : new HashSet<>(right);
        return left == null ? List.of() : left.stream().filter(Objects::nonNull)
                .filter(value -> !rightSet.contains(value)).distinct().toList();
    }

    /** 中文注释：计算请求、快照中实际关联的全部标签 ID。 */
    private List<Integer> allTagIds(MemeEditRequestDTO content) {
        if (content == null) {
            return List.of();
        }
        Set<Integer> ids = new TreeSet<>();
        if (content.getPrimaryTagId() != null) {
            ids.add(content.getPrimaryTagId());
        }
        if (content.getTagIds() != null) {
            content.getTagIds().stream().filter(Objects::nonNull).forEach(ids::add);
        }
        return List.copyOf(ids);
    }

    /** 中文注释：使用旧版快照字段布局计算摘要，以便升级前已提交的修改稿继续通过基线校验。 */
    private String legacySnapshotHash(MemeEditRequestDTO content) {
        try {
            ObjectNode legacy = objectMapper.valueToTree(content);
            legacy.remove("primaryTagId");
            legacy.set("tagIds", objectMapper.valueToTree(allTagIds(content)));
            return sha256(objectMapper.writeValueAsString(legacy));
        } catch (Exception exception) {
            throw new BizException(Result.CODE_ERROR, "正式内容读取失败");
        }
    }

    private void scheduleAfterCommitEffects(Long userId, Long memeId) {
        afterCommitExecutor.execute(() -> {
            try {
                if (memeId != null) {
                    memeService.evictMemeDetailCache(memeId);
                }
            } catch (Exception exception) {
                log.warn("清理梗详情缓存失败 memeId={}", memeId, exception);
            }
            try {
                if (userId != null) {
                    userProfileService.evictUserMemesCache(userId);
                }
            } catch (Exception exception) {
                log.warn("清理用户梗缓存失败 userId={}", userId, exception);
            }
        });
    }

    private void requireAdminId(Long adminUserId) {
        if (adminUserId == null || adminUserId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "管理员登录已失效");
        }
    }

    private boolean isEditableStatus(Integer status) {
        return Objects.equals(status, MemeVisibilitySupport.STATUS_NORMAL)
                || Objects.equals(status, MemeVisibilitySupport.STATUS_OFFLINE);
    }

    private String serialize(MemeEditRequestDTO content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception exception) {
            throw new BizException(Result.CODE_ERROR, "正式内容读取失败");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

}
