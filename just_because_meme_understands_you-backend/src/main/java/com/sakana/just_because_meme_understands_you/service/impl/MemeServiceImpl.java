package com.sakana.just_because_meme_understands_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.service.IMemeResourceService;
import com.sakana.just_because_meme_understands_you.service.IMemeService;
import com.sakana.just_because_meme_understands_you.service.IMemeTagRelationService;
import com.sakana.just_because_meme_understands_you.service.IMemeTagService;
import com.sakana.just_because_meme_understands_you.vo.MemeDetailVO;
import com.sakana.just_because_meme_understands_you.vo.MemeListItemVO;
import com.sakana.just_because_meme_understands_you.vo.MemeResourceVO;
import com.sakana.just_because_meme_understands_you.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.vo.SimpleMemeVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Resource
    private IMemeTagRelationService memeTagRelationService;

    @Resource
    private IMemeTagService memeTagService;

    @Resource
    private IMemeResourceService memeResourceService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public List<MemeListItemVO> pageMemeList(int page) {
        if (page <= 0) {
            page = 1;
        }
        // 每页固定 8 条，根据前端传递的页数分页返回
        int pageSize = IMemeService.PAGE_SIZE;

        Page<Meme> mpPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<Meme> wrapper = new LambdaQueryWrapper<Meme>()
                .eq(Meme::getStatus, 1)
                .orderByDesc(Meme::getLikes);

        Page<Meme> resultPage = this.page(mpPage, wrapper);
        List<Meme> memeList = resultPage.getRecords();
        if (memeList == null || memeList.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集本页所有梗的 id
        List<Integer> memeIds = memeList.stream()
                .map(Meme::getId)
                .toList();

        Map<Integer, List<MemeTag>> memeIdToTags = buildMemeIdToTags(memeIds);

        // 组装 VO
        List<MemeListItemVO> voList = new ArrayList<>(memeList.size());
        for (Meme meme : memeList) {
            MemeListItemVO vo = new MemeListItemVO();
            vo.setId(meme.getId());
            vo.setName(meme.getName());
            vo.setImage(meme.getImage());
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
            vo.setImage(meme.getImage());
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
            vo.setImage(meme.getImage());
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
    public MemeDetailVO getMemeDetail(Integer memeId) {
        if (memeId == null || memeId <= 0) {
            return null;
        }
        String cacheKey = DETAIL_CACHE_KEY_PREFIX + memeId;

        // 1. 先查 Redis 缓存
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json != null && !json.isEmpty()) {
                MemeDetailVO cached = objectMapper.readValue(json, new TypeReference<>() {});
                if (cached != null) {
                    return cached;
                }
            }
        } catch (Exception e) {
            log.warn("读取梗详情缓存失败, memeId={}", memeId, e);
        }

        // 2. 缓存未命中：查 DB 并组装 VO
        Meme meme = this.getById(memeId);
        if (meme == null) {
            return null;
        }
        List<MemeTag> tags = getTagsByMemeId(memeId);
        List<MemeResource> links = getLinksByMemeId(memeId);
        MemeDetailVO detailVO = buildDetailVO(meme, tags, links);

        // 3. 写入 Redis
        try {
            String jsonValue = objectMapper.writeValueAsString(detailVO);
            if (jsonValue != null) {
                stringRedisTemplate.opsForValue().set(cacheKey, jsonValue, DETAIL_CACHE_MINUTES, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("写入梗详情缓存失败, memeId={}", memeId, e);
        }
        return detailVO;
    }

    /**
     * 根据梗 id 查询关联的标签列表（通过 meme_tag_relation + meme_tag）
     */
    private List<MemeTag> getTagsByMemeId(Integer memeId) {
        List<MemeTagRelation> relations = memeTagRelationService.list(
                new LambdaQueryWrapper<MemeTagRelation>()
                        .eq(MemeTagRelation::getMemeId, memeId)
        );
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Integer> tagIds = relations.stream()
                .map(MemeTagRelation::getMemeTagId)
                .collect(Collectors.toSet());
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<MemeTag> tags = memeTagService.listByIds(tagIds);
        return tags != null ? tags : Collections.emptyList();
    }

    /**
     * 根据梗 id 查询关联的相关链接（meme_resource）
     */
    private List<MemeResource> getLinksByMemeId(Integer memeId) {
        List<MemeResource> resources = memeResourceService.list(
                new LambdaQueryWrapper<MemeResource>()
                        .eq(MemeResource::getMemeId, memeId.longValue())
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
        vo.setImage(meme.getImage());
        vo.setPageViews(meme.getPageViews());
        vo.setLikes(meme.getLikes());
        vo.setComments(meme.getComments());
        vo.setReleaseTime(meme.getReleaseTime());
        vo.setUpdateTime(meme.getUpdateTime());
        vo.setStatus(meme.getStatus());
        vo.setMemeTag(toMemeTagVOList(tags));
        vo.setLinks(toMemeResourceVOList(links));
        return vo;
    }

    private Map<Integer, List<MemeTag>> buildMemeIdToTags(List<Integer> memeIds) {
        if (memeIds == null || memeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MemeTagRelation> relations = memeTagRelationService.list(
                new LambdaQueryWrapper<MemeTagRelation>()
                        .in(MemeTagRelation::getMemeId, memeIds)
        );
        Map<Integer, List<MemeTag>> memeIdToTags = new HashMap<>();
        if (relations == null || relations.isEmpty()) {
            return memeIdToTags;
        }
        Set<Integer> tagIds = relations.stream()
                .map(MemeTagRelation::getMemeTagId)
                .collect(Collectors.toSet());
        if (tagIds.isEmpty()) {
            return memeIdToTags;
        }
        List<MemeTag> tags = memeTagService.listByIds(tagIds);
        Map<Integer, MemeTag> tagMap = tags.stream()
                .collect(Collectors.toMap(MemeTag::getId, t -> t));
        for (MemeTagRelation relation : relations) {
            Integer memeId = relation.getMemeId();
            Integer tagId = relation.getMemeTagId();
            MemeTag tag = tagMap.get(tagId);
            if (tag == null) {
                continue;
            }
            memeIdToTags
                    .computeIfAbsent(memeId, k -> new ArrayList<>())
                    .add(tag);
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
            vo.setResourceUrl(Collections.singletonList(url.trim()));
            list.add(vo);
        }
        return list;
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
}

