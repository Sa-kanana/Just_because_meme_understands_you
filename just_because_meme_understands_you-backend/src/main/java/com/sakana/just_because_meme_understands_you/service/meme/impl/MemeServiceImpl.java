package com.sakana.just_because_meme_understands_you.service.meme.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.dto.MemeTagBindDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.common.MemeResourceType;
import com.sakana.just_because_meme_understands_you.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.entity.UserRelation;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserRelationMapper;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeResourceService;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.service.comment.MemeCommentCountService;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.service.meme.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.service.meme.support.MemeVisibilitySupport.ViewAccess;
import com.sakana.just_because_meme_understands_you.vo.MemeDetailVO;
import com.sakana.just_because_meme_understands_you.vo.MemeListItemVO;
import com.sakana.just_because_meme_understands_you.vo.MemeResourceVO;
import com.sakana.just_because_meme_understands_you.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.SimpleMemeVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 梗服务实现：分页列表、搜索、详细页等；详细页使用 Redis 缓存。
 *
 * @author sakana
 * @since 2026-03-06
 */
@Slf4j
@Service
public class MemeServiceImpl extends ServiceImpl<MemeMapper, Meme> implements IMemeService {

    /** 梗详情 Redis key 前缀，key: meme:detail:{memeId}，TTL 10 分钟 */
    private static final String DETAIL_CACHE_KEY_PREFIX = "meme:detail:";
    private static final long DETAIL_CACHE_MINUTES = 10;
    private static final int STATUS_NORMAL = 1;

    @Resource
    private MemeTagRelationMapper memeTagRelationMapper;

    @Resource
    private IMemeResourceService memeResourceService;

    @Resource
    private MemeCommentCountService memeCommentCountService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private UserRelationMapper userRelationMapper;

    @Override
    public List<MemeListItemVO> pageMemeList(int page) {
        PageVO<MemeListItemVO> pageVO = pageMemeFeed(page, IMemeService.PAGE_SIZE, "hot", null);
        return pageVO.getList() != null ? pageVO.getList() : Collections.emptyList();
    }

    @Override
    public PageVO<MemeListItemVO> pageMemeFeed(int page, int size, String sort, Long followingUserId) {
        if (page <= 0) {
            page = 1;
        }
        if (size <= 0) {
            size = IMemeService.PAGE_SIZE;
        }
        size = Math.min(size, 32);

        String normalizedSort = normalizeSort(sort);
        Page<Meme> mpPage = new Page<>(page, size);
        LambdaQueryWrapper<Meme> wrapper = new LambdaQueryWrapper<Meme>()
                .eq(Meme::getStatus, STATUS_NORMAL);
        applyFeedSort(wrapper, normalizedSort, followingUserId);

        Page<Meme> resultPage = this.page(mpPage, wrapper);
        List<Meme> memeList = resultPage.getRecords();
        List<MemeListItemVO> voList = toMemeListItemVOs(memeList);

        PageVO<MemeListItemVO> pageVO = new PageVO<>();
        pageVO.setList(voList);
        pageVO.setPage(page);
        pageVO.setSize(size);
        pageVO.setTotal(resultPage.getTotal());
        pageVO.setHasMore(resultPage.getCurrent() * resultPage.getSize() < resultPage.getTotal());
        return pageVO;
    }

    @Override
    public List<MemeListItemVO> listHotMemes(int limit) {
        if (limit <= 0) {
            limit = 6;
        }
        limit = Math.min(limit, 12);

        Page<Meme> mpPage = new Page<>(1, limit);
        LambdaQueryWrapper<Meme> wrapper = new LambdaQueryWrapper<Meme>()
                .eq(Meme::getStatus, STATUS_NORMAL)
                .orderByDesc(Meme::getLikes)
                .orderByDesc(Meme::getComments)
                .orderByDesc(Meme::getPageViews)
                .orderByDesc(Meme::getId);
        Page<Meme> resultPage = this.page(mpPage, wrapper);
        return toMemeListItemVOs(resultPage.getRecords());
    }

    @Override
    public List<MemeListItemVO> searchByKeyword(String keyword, int page) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        if (page <= 0) {
            page = 1;
        }
        String trimmed = keyword.trim();
        int pageSize = IMemeService.PAGE_SIZE;
        Page<Meme> mpPage = new Page<>(page, pageSize);
        IPage<Meme> resultPage = baseMapper.searchByKeyword(mpPage, trimmed, null, null, null);
        List<Meme> memeList = resultPage.getRecords();
        if (memeList == null || memeList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> memeIds = memeList.stream().map(Meme::getId).toList();
        Map<Integer, List<MemeTag>> memeIdToTags = buildMemeIdToTags(memeIds);

        List<MemeListItemVO> voList = new ArrayList<>(memeList.size());
        for (Meme meme : memeList) {
            MemeListItemVO vo = new MemeListItemVO();
            vo.setId(meme.getId());
            vo.setName(meme.getName());
            vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
            vo.setPageViews(meme.getPageViews());
            vo.setLikes(meme.getLikes());
            vo.setComments(meme.getComments());
            vo.setReleaseTime(meme.getReleaseTime());
            vo.setUpdateTime(meme.getUpdateTime());
            vo.setStatus(meme.getStatus());
            List<MemeTag> tags = memeIdToTags.getOrDefault(meme.getId(), Collections.emptyList());
            vo.setMemeTag(toMemeTagVOList(tags));
            vo.setLabel(tags);
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public List<SimpleMemeVO> searchByKeywordForApi(String keyword, String mostLikes, String mostViews, String mostComments) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = keyword.trim();
        Page<Meme> mpPage = new Page<>(1, IMemeService.PAGE_SIZE);
        IPage<Meme> resultPage = baseMapper.searchByKeyword(mpPage, trimmed, mostLikes, mostViews, mostComments);
        List<Meme> memeList = resultPage.getRecords();
        if (memeList == null || memeList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> memeIds = memeList.stream().map(Meme::getId).toList();
        Map<Integer, List<MemeTag>> memeIdToTags = buildMemeIdToTags(memeIds);

        List<SimpleMemeVO> voList = new ArrayList<>(memeList.size());
        for (Meme meme : memeList) {
            SimpleMemeVO vo = new SimpleMemeVO();
            vo.setId(meme.getId());
            vo.setName(meme.getName());
            vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
            vo.setPageViews(meme.getPageViews());
            vo.setLikes(meme.getLikes());
            vo.setComments(meme.getComments());
            vo.setReleaseTime(meme.getReleaseTime());
            vo.setUpdateTime(meme.getUpdateTime());
             vo.setStatus(meme.getStatus());
            List<MemeTag> tags = memeIdToTags.getOrDefault(meme.getId(), Collections.emptyList());
            vo.setMemeTag(toMemeTagVOList(tags));
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public MemeDetailVO getMemeDetail(Integer memeId, Long currentUserId) {
        if (memeId == null || memeId <= 0) {
            return null;
        }

        Meme meme = this.getById(memeId);
        if (meme == null) {
            return null;
        }
        ViewAccess access = MemeVisibilitySupport.resolveViewAccess(meme, currentUserId);
        if (access == ViewAccess.FORBIDDEN) {
            return null;
        }

        String cacheKey = DETAIL_CACHE_KEY_PREFIX + memeId;
        if (access == ViewAccess.PUBLIC) {
            try {
                String json = stringRedisTemplate.opsForValue().get(cacheKey);
                if (json != null && !json.isEmpty()) {
                    MemeDetailVO cached = objectMapper.readValue(json, new TypeReference<>() {});
                    if (cached != null && Objects.equals(cached.getStatus(), STATUS_NORMAL)) {
                        applyViewMeta(cached, meme, currentUserId, access);
                        ossUrlHelper.refreshMemeDetailUrls(cached);
                        return cached;
                    }
                    stringRedisTemplate.delete(cacheKey);
                }
            } catch (Exception e) {
                log.warn("读取梗详情缓存失败, memeId={}", memeId, e);
            }
        }

        List<MemeTag> tags = getTagsByMemeId(memeId);
        List<MemeResource> links = getLinksByMemeId(memeId);
        MemeDetailVO detailVO = buildDetailVO(meme, tags, links);
        applyViewMeta(detailVO, meme, currentUserId, access);

        if (access == ViewAccess.PUBLIC) {
            try {
                String jsonValue = objectMapper.writeValueAsString(detailVO);
                if (jsonValue != null) {
                    stringRedisTemplate.opsForValue().set(cacheKey, jsonValue, DETAIL_CACHE_MINUTES, TimeUnit.MINUTES);
                }
            } catch (Exception e) {
                log.warn("写入梗详情缓存失败, memeId={}", memeId, e);
            }
        }
        return detailVO;
    }

    private void applyViewMeta(MemeDetailVO vo, Meme meme, Long currentUserId, ViewAccess access) {
        if (vo == null || meme == null) {
            return;
        }
        vo.setStatus(meme.getStatus());
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(meme.getStatus()));
        vo.setOwner(MemeVisibilitySupport.isOwner(meme, currentUserId));
        vo.setOwnerPreview(access == ViewAccess.OWNER_PREVIEW);
        vo.setCommentsEnabled(access == ViewAccess.PUBLIC);
        vo.setViewMode(access == ViewAccess.PUBLIC ? "public" : "owner_preview");
        vo.setFavoriteEnabled(access == ViewAccess.PUBLIC);
    }

    /**
     * 根据梗 id 查询关联的标签列表（一次 JOIN 查询）
     */
    private List<MemeTag> getTagsByMemeId(Integer memeId) {
        return buildMemeIdToTags(java.util.Collections.singletonList(memeId))
                .getOrDefault(memeId, java.util.Collections.emptyList());
    }

    /**
     * 根据梗 id 查询关联的相关链接（meme_resource）
     */
    private List<MemeResource> getLinksByMemeId(Integer memeId) {
        List<MemeResource> resources = memeResourceService.list(
                new LambdaQueryWrapper<MemeResource>()
                        .eq(MemeResource::getMemeId, memeId.longValue())
                        .eq(MemeResource::getStatus, 1)
                        .orderByAsc(MemeResource::getSortOrder)
                        .orderByAsc(MemeResource::getId)
        );
        return resources != null ? resources : Collections.emptyList();
    }

    /**
     * 根据梗实体、标签与链接列表组装详细页 VO（符合接口 Meme）
     */
    private MemeDetailVO buildDetailVO(Meme meme, List<MemeTag> tags, List<MemeResource> links) {
        MemeDetailVO vo = new MemeDetailVO();
        vo.setId(meme.getId());
        vo.setIntroduction(meme.getIntroduction());
        vo.setName(meme.getName());
        vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
        vo.setPageViews(meme.getPageViews());
        vo.setLikes(meme.getLikes());
        if (meme.getId() != null) {
            vo.setComments(memeCommentCountService.getCommentCount(meme.getId().longValue()));
        } else {
            vo.setComments(meme.getComments());
        }
        vo.setReleaseTime(meme.getReleaseTime());
        vo.setUpdateTime(meme.getUpdateTime());
        vo.setStatus(meme.getStatus());
        vo.setMemeTag(toMemeTagVOList(tags));
        vo.setLinks(toMemeResourceVOList(links));
        return vo;
    }

    /**
     * 一次 JOIN 查询批量取回多个梗的标签，按梗 id 分组。
     * 替代原先「查关系表 + 批量查标签表」两次查询。
     */
    private Map<Integer, List<MemeTag>> buildMemeIdToTags(List<Integer> memeIds) {
        if (memeIds == null || memeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MemeTagBindDTO> rows = memeTagRelationMapper.selectTagsByMemeIds(memeIds);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<MemeTag>> memeIdToTags = new HashMap<>();
        for (MemeTagBindDTO row : rows) {
            MemeTag tag = new MemeTag();
            tag.setId(row.getId());
            tag.setName(row.getName());
            tag.setRelatedQuantity(row.getRelatedQuantity());
            memeIdToTags.computeIfAbsent(row.getMemeId(), k -> new ArrayList<>()).add(tag);
        }
        return memeIdToTags;
    }

    private List<MemeTagVO> toMemeTagVOList(List<MemeTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        List<MemeTagVO> list = new ArrayList<>(tags.size());
        for (MemeTag t : tags) {
            MemeTagVO vo = new MemeTagVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            vo.setRelatedQuantity(parseRelatedQuantity(t.getRelatedQuantity()));
            list.add(vo);
        }
        return list;
    }

    private List<MemeResourceVO> toMemeResourceVOList(List<MemeResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return Collections.emptyList();
        }
        List<MemeResourceVO> list = new ArrayList<>(resources.size());
        for (MemeResource resource : resources) {
            String url = resource.getResourceUrl();
            if (url == null || url.isBlank()) {
                continue;
            }
            MemeResourceVO vo = new MemeResourceVO();
            if (resource.getId() != null) {
                vo.setId(resource.getId().intValue());
            }
            MemeResourceType type = MemeResourceType.fromCode(resource.getResourceType());
            String storedUrl = url.trim();
            if (type == MemeResourceType.LINK && !StringUtils.hasText(resource.getTitle())) {
                String key = ossUrlHelper.normalizeForStorage(storedUrl);
                if (!key.toLowerCase().startsWith("http")) {
                    type = MemeResourceType.MEDIA;
                }
            }
            vo.setType(type.getApiType());
            vo.setTitle(resource.getTitle());
            vo.setUrl(ossUrlHelper.toPublicUrl(storedUrl));
            vo.setSortOrder(resource.getSortOrder() != null ? resource.getSortOrder() : 0);
            list.add(vo);
        }
        return list;
    }

    @Override
    public void evictMemeDetailCache(Long memeId) {
        if (memeId == null || memeId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(DETAIL_CACHE_KEY_PREFIX + memeId);
        } catch (Exception e) {
            log.warn("清理梗详情缓存失败, memeId={}", memeId, e);
        }
    }

    private static Integer parseRelatedQuantity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<MemeListItemVO> toMemeListItemVOs(List<Meme> memeList) {
        if (memeList == null || memeList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> memeIds = memeList.stream().map(Meme::getId).toList();
        Map<Integer, List<MemeTag>> memeIdToTags = buildMemeIdToTags(memeIds);

        List<MemeListItemVO> voList = new ArrayList<>(memeList.size());
        for (Meme meme : memeList) {
            MemeListItemVO vo = new MemeListItemVO();
            vo.setId(meme.getId());
            vo.setName(meme.getName());
            vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
            vo.setPageViews(meme.getPageViews());
            vo.setLikes(meme.getLikes());
            vo.setComments(meme.getComments());
            vo.setReleaseTime(meme.getReleaseTime());
            vo.setUpdateTime(meme.getUpdateTime());
            vo.setStatus(meme.getStatus());
            List<MemeTag> tags = memeIdToTags.getOrDefault(meme.getId(), Collections.emptyList());
            vo.setMemeTag(toMemeTagVOList(tags));
            vo.setLabel(tags);
            voList.add(vo);
        }
        return voList;
    }

    private void applyFeedSort(LambdaQueryWrapper<Meme> wrapper, String sort, Long followingUserId) {
        if ("following".equals(sort)) {
            List<Long> followingIds = listFollowingUserIds(followingUserId);
            if (followingIds.isEmpty()) {
                wrapper.eq(Meme::getId, -1);
            } else {
                wrapper.in(Meme::getUserId, followingIds);
            }
            wrapper.orderByDesc(Meme::getReleaseTime).orderByDesc(Meme::getId);
            return;
        }
        switch (sort) {
            case "new" -> wrapper.orderByDesc(Meme::getReleaseTime).orderByDesc(Meme::getId);
            case "comments" -> wrapper.orderByDesc(Meme::getComments).orderByDesc(Meme::getId);
            case "views" -> wrapper.orderByDesc(Meme::getPageViews).orderByDesc(Meme::getId);
            default -> wrapper.orderByDesc(Meme::getLikes).orderByDesc(Meme::getId);
        }
    }

    private List<Long> listFollowingUserIds(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        List<UserRelation> relations = userRelationMapper.selectList(new LambdaQueryWrapper<UserRelation>()
                .eq(UserRelation::getFromUserId, userId));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream()
                .map(UserRelation::getToUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "hot";
        }
        String normalized = sort.trim().toLowerCase();
        return switch (normalized) {
            case "hot", "new", "comments", "views", "following" -> normalized;
            default -> "hot";
        };
    }
}

