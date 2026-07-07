package com.sakana.just_because_meme_understands_you.service.meme;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.MemeResourceType;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.MemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeResourceItemDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.service.user.IUserProfileService;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.vo.MemeCreateResponseVO;
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
import java.util.HashSet;
import java.util.List;
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
    private MemePublishAsyncHandler memePublishAsyncHandler;

    @Resource
    private IUserProfileService userProfileService;

    @Resource
    private OssUrlHelper ossUrlHelper;

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

        List<Integer> tagIds = normalizeTagIds(request.getTagIds());
        validateTagsExist(tagIds);

        LocalDateTime now = LocalDateTime.now();

        String imageKey = ossUrlHelper.normalizeForStorage(request.getImage());
        ossUrlHelper.assertOwnedImageKey(imageKey, "memes/");

        // 1. 写入 meme 主表
        Meme meme = new Meme();
        meme.setName(request.getName().trim());
        meme.setIntroduction(request.getIntroduction().trim());
        meme.setImage(imageKey);
        meme.setPageViews(0);
        meme.setLikes(0);
        meme.setComments(0);
        meme.setStatus(DEFAULT_STATUS);
        meme.setReleaseTime(now);
        meme.setUpdateTime(now);
        meme.setUserId(userId);
        memeMapper.insert(meme);
        Integer memeId = meme.getId();
        if (memeId == null) {
            throw new BizException(Result.CODE_ERROR, "梗主表写入失败");
        }

        // 2. 写入 meme_resource 资源表
        saveMediaResources(memeId, request.getResourceUrls());
        saveStructuredLinks(memeId, request.getResources());

        // 3. 写入 meme_tag_relation 关联表
        saveTagRelations(memeId, tagIds);

        // 4. 非核心：标签计数 + 布隆热加载 + 缓存清理，事务提交后异步执行
        scheduleAfterPublishCommitted(userId, memeId, tagIds);

        MemeCreateResponseVO vo = new MemeCreateResponseVO();
        vo.setMemeId(memeId);
        vo.setStatus(DEFAULT_STATUS);
        vo.setStatusDesc(DEFAULT_STATUS_DESC);
        return vo;
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
        if (!StringUtils.hasText(request.getIntroduction())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "梗介绍不能为空");
        }
        if (!StringUtils.hasText(request.getImage())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "封面图不能为空");
        }
    }

    private List<Integer> normalizeTagIds(List<Integer> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "至少选择一个标签");
        }
        Set<Integer> distinct = new HashSet<>();
        for (Integer id : tagIds) {
            if (id != null && id > 0) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "标签 id 不合法");
        }
        return distinct.stream().toList();
    }

    /**
     * 校验传入的标签 id 全部存在，任一不存在则拒绝发布，保证关联表数据完整。
     */
    private void validateTagsExist(List<Integer> tagIds) {
        List<MemeTag> existTags = memeTagMapper.selectBatchIds(tagIds);
        if (existTags == null || existTags.size() != tagIds.size()) {
            Set<Integer> existIds = new HashSet<>();
            for (MemeTag t : existTags) {
                if (t != null && t.getId() != null) {
                    existIds.add(t.getId());
                }
            }
            for (Integer id : tagIds) {
                if (!existIds.contains(id)) {
                    throw new BizException(Result.CODE_BAD_REQUEST, "标签不存在, tagId=" + id);
                }
            }
        }
    }

    private void saveMediaResources(Integer memeId, List<String> resourceUrls) {
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
            String normalized = ossUrlHelper.normalizeForStorage(url);
            if (!normalized.toLowerCase().startsWith("http")) {
                ossUrlHelper.assertOwnedImageKey(normalized, "memes/", "home/", "common/");
            }
            MemeResource resource = new MemeResource();
            resource.setMemeId(memeId.longValue());
            resource.setResourceUrl(normalized);
            resource.setResourceType(MemeResourceType.MEDIA.getCode());
            resource.setSortOrder(sortOrder++);
            resource.setStatus(RESOURCE_STATUS_ACTIVE);
            resource.setCreateTime(now);
            memeResourceMapper.insert(resource);
        }
    }

    private void saveStructuredLinks(Integer memeId, List<MemeResourceItemDTO> resources) {
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
            resource.setMemeId(memeId.longValue());
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

    private void saveTagRelations(Integer memeId, List<Integer> tagIds) {
        for (Integer tagId : tagIds) {
            MemeTagRelation relation = new MemeTagRelation();
            relation.setMemeId(memeId);
            relation.setMemeTagId(tagId);
            memeTagRelationMapper.insert(relation);
        }
    }

    /**
     * 事务提交后再触发异步链路，避免主事务回滚却已累加计数 / 加入布隆。
     */
    private void scheduleAfterPublishCommitted(Long userId, Integer memeId, List<Integer> tagIds) {
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

    private void runAfterPublish(Long userId, Integer memeId, List<Integer> tagIds) {
        // 布隆过滤器热加载：同步追加，保证后续评论/收藏不被误拦
        try {
            memeBloomFilterService.add(memeId);
        } catch (Exception e) {
            log.warn("布隆过滤器追加失败, memeId={}", memeId, e);
        }
        // 标签计数异步累加
        try {
            memePublishAsyncHandler.incrementTagRelatedQuantity(tagIds);
        } catch (Exception e) {
            log.warn("标签计数异步任务派发失败, memeId={}", memeId, e);
        }
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
    public boolean mightExist(Integer memeId) {
        return memeBloomFilterService.mightContain(memeId);
    }
}
