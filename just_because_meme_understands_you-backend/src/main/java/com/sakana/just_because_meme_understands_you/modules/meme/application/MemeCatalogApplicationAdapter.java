package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.modules.meme.application.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.config.MemeCrawlProperties;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeEditRevision;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeEditRevisionMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.projection.MemeTagBindDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 梗图模块内部适配器：将公开契约转换为本模块的数据库查询。 */
@Service
public class MemeCatalogApplicationAdapter implements MemeCatalogPort {

    private static final int DEFAULT_SEARCH_LIMIT = 8;
    private static final int MAX_SEARCH_LIMIT = 100;
    /** 中文注释：梗向量双向校准每次从 Java 已发布梗目录读取的最大 ID 数。 */
    private static final int MAX_PUBLISHED_ID_PAGE_SIZE = 50;

    private final MemeMapper memeMapper;
    private final MemeEditRevisionMapper memeEditRevisionMapper;
    private final MemeTagMapper memeTagMapper;
    private final MemeTagRelationMapper memeTagRelationMapper;
    private final MemeResourceMapper memeResourceMapper;
    private final MemeBloomFilterService memeBloomFilterService;
    private final MemeCrawlProperties memeCrawlProperties;

    public MemeCatalogApplicationAdapter(
            MemeMapper memeMapper,
            MemeEditRevisionMapper memeEditRevisionMapper,
            MemeTagMapper memeTagMapper,
            MemeTagRelationMapper memeTagRelationMapper,
            MemeResourceMapper memeResourceMapper,
            MemeBloomFilterService memeBloomFilterService,
            MemeCrawlProperties memeCrawlProperties) {
        this.memeMapper = memeMapper;
        this.memeEditRevisionMapper = memeEditRevisionMapper;
        this.memeTagMapper = memeTagMapper;
        this.memeTagRelationMapper = memeTagRelationMapper;
        this.memeResourceMapper = memeResourceMapper;
        this.memeBloomFilterService = memeBloomFilterService;
        this.memeCrawlProperties = memeCrawlProperties;
    }

    @Override
    public List<MemeCard> searchPublishedByKeyword(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int normalizedLimit = limit <= 0 ? DEFAULT_SEARCH_LIMIT : Math.min(limit, MAX_SEARCH_LIMIT);
        String searchKey = keyword.trim();
        if (searchKey.length() > 64) {
            searchKey = searchKey.substring(0, 64);
        }
        IPage<Meme> result = memeMapper.searchByKeyword(
                new Page<>(1, normalizedLimit, false), searchKey, "1", null, null, null);
        if (result == null || result.getRecords() == null) {
            return List.of();
        }
        return result.getRecords().stream()
                .filter(Objects::nonNull)
                .filter(meme -> Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_NORMAL))
                .map(this::toCard)
                .toList();
    }

    @Override
    public MemeCard findById(long memeId) {
        if (memeId <= 0) {
            return null;
        }
        return toCard(memeMapper.selectById(memeId));
    }

    @Override
    public MemeCard findPublishedById(long memeId) {
        if (memeId <= 0 || !memeBloomFilterService.mightContain(memeId)) {
            return null;
        }
        Meme meme = memeMapper.selectById(memeId);
        return meme != null && Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_NORMAL)
                ? toCard(meme)
                : null;
    }

    @Override
    public List<MemeCard> findByIds(Collection<Long> memeIds) {
        List<Long> ids = normalizeMemeIds(memeIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return memeMapper.selectBatchIds(ids).stream().map(this::toCard).toList();
    }

    @Override
    public List<MemeCard> findPublishedByIds(Collection<Long> memeIds) {
        List<Long> ids = normalizeMemeIds(memeIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return memeMapper.selectList(new LambdaQueryWrapper<Meme>()
                        .in(Meme::getId, ids)
                        .eq(Meme::getStatus, MemeVisibilitySupport.STATUS_NORMAL))
                .stream().map(this::toCard).toList();
    }

    @Override
    public List<Long> pagePublishedIdsAfterId(long afterMemeId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_PUBLISHED_ID_PAGE_SIZE));
        List<Long> ids = memeMapper.selectPublishedIdsAfterId(Math.max(0, afterMemeId), safeLimit);
        return ids == null ? List.of() : ids;
    }

    @Override
    public MemePage pageByAuthor(long userId, boolean owner, Integer status, int page, int size,
                                 MemeSort sort, String keyword) {
        int pageNumber = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(size, 100));
        LambdaQueryWrapper<Meme> wrapper = new LambdaQueryWrapper<Meme>().eq(Meme::getUserId, userId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Meme::getName, keyword.trim());
        }
        applyAuthorSort(wrapper, sort == null ? MemeSort.LATEST : sort);
        if (!owner) {
            wrapper.eq(Meme::getStatus, MemeVisibilitySupport.STATUS_NORMAL);
        } else {
            wrapper.ne(Meme::getStatus, MemeVisibilitySupport.STATUS_PURGED);
            applyOwnerStatusFilter(wrapper, status);
        }
        IPage<Meme> result = memeMapper.selectPage(new Page<>(pageNumber, pageSize), wrapper);
        return new MemePage(result.getRecords().stream().map(this::toCard).toList(), result.getTotal());
    }

    /** 使用固定字段白名单排序，并以发布时间和 ID 作为稳定的并列顺序。 */
    private void applyAuthorSort(LambdaQueryWrapper<Meme> wrapper, MemeSort sort) {
        switch (sort) {
            case VIEWS -> wrapper.orderByDesc(Meme::getPageViews);
            case LIKES -> wrapper.orderByDesc(Meme::getLikes);
            case COMMENTS -> wrapper.orderByDesc(Meme::getComments);
            case LATEST -> {
                // 发布时间在下面统一追加，最新排序不需要额外主排序字段。
            }
        }
        wrapper.orderByDesc(Meme::getReleaseTime).orderByDesc(Meme::getId);
    }

    @Override
    public MemeStatusCounts countByAuthor(long userId) {
        long live = countByStatus(userId, List.of(MemeVisibilitySupport.STATUS_NORMAL));
        long reviewing = countByStatus(userId, List.of(
                MemeVisibilitySupport.STATUS_REVIEWING, MemeVisibilitySupport.STATUS_RESTORE_REVIEWING));
        long offline = countByStatus(userId, List.of(MemeVisibilitySupport.STATUS_OFFLINE));
        long locked = countByStatus(userId, List.of(MemeVisibilitySupport.STATUS_LOCKED));
        return new MemeStatusCounts(live, reviewing, offline, locked);
    }

    @Override
    public long countPublishedByAuthor(long userId) {
        return userId <= 0 ? 0 : countByStatus(userId, List.of(MemeVisibilitySupport.STATUS_NORMAL));
    }

    @Override
    public List<MemeCard> findLatestPublishedByAuthor(long userId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int safeLimit = Math.min(limit, MAX_SEARCH_LIMIT);
        return memeMapper.selectList(new LambdaQueryWrapper<Meme>()
                        .eq(Meme::getUserId, userId)
                        .eq(Meme::getStatus, MemeVisibilitySupport.STATUS_NORMAL)
                        .orderByDesc(Meme::getReleaseTime)
                        .last("LIMIT " + safeLimit))
                .stream().map(this::toCard).toList();
    }

    @Override
    public Map<Long, List<MemeTagSummary>> findTagsByMemeIds(Collection<Long> memeIds) {
        List<Long> ids = normalizeMemeIds(memeIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<MemeTagBindDTO> rows = memeTagRelationMapper.selectTagsByMemeIds(ids);
        Map<Long, List<MemeTagSummary>> grouped = new HashMap<>();
        if (rows == null) {
            return grouped;
        }
        for (MemeTagBindDTO row : rows) {
            if (row == null || row.getMemeId() == null || !StringUtils.hasText(row.getName())) {
                continue;
            }
            grouped.computeIfAbsent(row.getMemeId().longValue(), ignored -> new ArrayList<>())
                    .add(new MemeTagSummary(row.getId() == null ? null : row.getId().longValue(),
                            row.getName().trim(), null, row.getCategory(), Boolean.TRUE.equals(row.getIsPrimary())));
        }
        return grouped;
    }

    @Override
    public List<MemeResourceSummary> findResourcesByMemeId(long memeId) {
        if (memeId <= 0) {
            return List.of();
        }
        List<MemeResource> resources = memeResourceMapper.selectList(new LambdaQueryWrapper<MemeResource>()
                .eq(MemeResource::getMemeId, memeId)
                .orderByAsc(MemeResource::getSortOrder)
                .orderByAsc(MemeResource::getId));
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        return resources.stream()
                .filter(Objects::nonNull)
                .map(resource -> new MemeResourceSummary(
                        resource.getId(), resource.getResourceType(), resource.getTitle(), resource.getResourceUrl(),
                        resource.getSortOrder(), resource.getStatus()))
                .toList();
    }

    @Override
    public Map<Long, MemeEditRevisionSummary> findLatestEditRevisionsByMemeIds(Collection<Long> memeIds) {
        List<Long> ids = normalizeMemeIds(memeIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, MemeEditRevisionSummary> summaries = new HashMap<>();
        List<MemeEditRevision> revisions = memeEditRevisionMapper.selectLatestByMemeIds(ids);
        if (revisions == null) {
            return summaries;
        }
        for (MemeEditRevision revision : revisions) {
            if (revision == null || revision.getMemeId() == null) {
                continue;
            }
            summaries.put(revision.getMemeId(), new MemeEditRevisionSummary(
                    revision.getStatus(), revision.getCreatedAt(), revision.getReviewNote()));
        }
        return summaries;
    }

    @Override
    public List<MemeTagSummary> findTagsByIds(Collection<Long> tagIds) {
        List<Integer> ids = normalizeTagIds(tagIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return memeTagMapper.selectBatchIds(ids).stream()
                .map(this::toTag)
                .toList();
    }

    @Override
    public List<MemeTagSummary> findHotTags(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return memeTagMapper.selectList(new LambdaQueryWrapper<MemeTag>()
                        .isNotNull(MemeTag::getName)
                        .ne(MemeTag::getName, ""))
                .stream()
                .map(this::toTag)
                .filter(tag -> tag != null && tag.relatedQuantity() != null && tag.relatedQuantity() > 0)
                .sorted(Comparator.comparing(MemeTagSummary::relatedQuantity).reversed()
                        .thenComparing(MemeTagSummary::id,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    @Override
    public MemeTagSummary findTagById(long tagId) {
        return tagId <= 0 ? null : toTag(memeTagMapper.selectById(tagId));
    }

    @Override
    public CrawlSettings crawlSettings() {
        return new CrawlSettings(memeCrawlProperties.isEnabled(), memeCrawlProperties.isScheduleEnabled(),
                memeCrawlProperties.getLimit(), memeCrawlProperties.getPublisherUserId(),
                memeCrawlProperties.getLiveLimit(), memeCrawlProperties.getLiveHours());
    }

    @Override
    public boolean isPublished(long memeId) {
        return findPublishedById(memeId) != null;
    }

    @Override
    public boolean isImageAssetReferenced(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }
        Long memeCoverCount = memeMapper.selectCount(new LambdaQueryWrapper<Meme>()
                .eq(Meme::getImage, objectKey));
        if (memeCoverCount != null && memeCoverCount > 0) {
            return true;
        }
        Long resourceCount = memeResourceMapper.selectCount(new LambdaQueryWrapper<MemeResource>()
                .eq(MemeResource::getResourceUrl, objectKey));
        if (resourceCount != null && resourceCount > 0) {
            return true;
        }
        Long pendingRevisionCount = memeEditRevisionMapper.selectCount(new LambdaQueryWrapper<MemeEditRevision>()
                .eq(MemeEditRevision::getStatus, "PENDING")
                .apply("JSON_SEARCH(content_json, 'one', {0}, NULL, '$.image', '$.resourceUrls[*]') IS NOT NULL",
                        objectKey));
        return pendingRevisionCount != null && pendingRevisionCount > 0;
    }

    @Override
    public String statusDescription(Integer status) {
        return MemeVisibilitySupport.statusDesc(status);
    }

    @Override
    public void adjustLikeCount(long memeId, int delta) {
        if (delta == 1) {
            memeMapper.incrementLikes(memeId);
        } else if (delta == -1) {
            memeMapper.decrementLikes(memeId);
        } else if (delta != 0) {
            throw new IllegalArgumentException("点赞数只允许调整 +1、-1 或 0");
        }
    }

    @Override
    public boolean incrementCommentCount(long memeId) {
        return memeId > 0 && memeMapper.incrementComments(memeId) == 1;
    }

    private long countByStatus(long userId, List<Integer> statuses) {
        return memeMapper.selectCount(new LambdaQueryWrapper<Meme>()
                .eq(Meme::getUserId, userId)
                .in(Meme::getStatus, statuses));
    }

    private void applyOwnerStatusFilter(LambdaQueryWrapper<Meme> wrapper, Integer status) {
        if (status == null) {
            return;
        }
        if (status == MemeVisibilitySupport.STATUS_REVIEWING) {
            wrapper.in(Meme::getStatus, MemeVisibilitySupport.STATUS_REVIEWING,
                    MemeVisibilitySupport.STATUS_RESTORE_REVIEWING);
        } else if (status == MemeVisibilitySupport.STATUS_NORMAL
                || status == MemeVisibilitySupport.STATUS_OFFLINE
                || status == MemeVisibilitySupport.STATUS_LOCKED
                || status == MemeVisibilitySupport.STATUS_RESTORE_REVIEWING) {
            wrapper.eq(Meme::getStatus, status);
        }
    }

    private List<Long> normalizeMemeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }

    private List<Integer> normalizeTagIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0 && id <= Integer.MAX_VALUE)
                .map(Long::intValue)
                .distinct()
                .toList();
    }

    private MemeCard toCard(Meme meme) {
        if (meme == null) {
            return null;
        }
        return new MemeCard(
                meme.getId(),
                meme.getUserId(), meme.getName(), meme.getIntroduction(), meme.getImage(),
                meme.getPageViews(), meme.getLikes(), meme.getComments(), meme.getStatus(),
                meme.getReleaseTime(), meme.getOfflineReason(), meme.getAppealRejectCount());
    }

    private MemeTagSummary toTag(com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag tag) {
        if (tag == null) {
            return null;
        }
        return new MemeTagSummary(tag.getId() == null ? null : tag.getId().longValue(),
                tag.getName(), tag.getRelatedQuantity(), tag.getCategory(), false);
    }
}
