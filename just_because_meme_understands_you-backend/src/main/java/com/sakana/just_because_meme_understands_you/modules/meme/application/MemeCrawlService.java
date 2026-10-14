package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.MemeResourceType;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCandidateSource;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeIndexEventPort;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.config.MemeCrawlProperties;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.application.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.api.admin.vo.MemeCrawlResultVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 热梗采集：Agent（B站理解 + Firecrawl 细节）→ MySQL 入库（SSOT）→ 布隆过滤器热更新 → 异步灌 pgvector。
 */
@Slf4j
@Service
public class MemeCrawlService {

    private static final int RESOURCE_STATUS_ACTIVE = 1;
    private static final int MAX_NAME = 80;
    /** 与 meme.introduction varchar(255) 对齐 */
    private static final int MAX_INTRO = 255;
    private static final int MAX_URL = 500;
    private static final String BILIBILI_VIDEO_URL_PREFIX = "https://www.bilibili.com/video/BV";
    private static final Pattern BILIBILI_VIDEO_ID_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?bilibili\\.com/video/(BV[0-9A-Za-z]+)(?:[/?#]|$)",
            Pattern.CASE_INSENSITIVE);

    @Resource
    private MemeCrawlProperties memeCrawlProperties;

    @Resource
    private MemeCandidateSource memeCandidateSource;

    @Resource
    private MemeMapper memeMapper;

    @Resource
    private MemeBloomFilterService memeBloomFilterService;

    @Resource
    private MemeResourceMapper memeResourceMapper;

    @Resource
    private MemeIndexEventPort memeIndexEventPort;

    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 拉取一次热梗并写入业务库（使用配置默认 limit）。
     */
    public MemeCrawlResultVO crawlAndPersist() {
        return crawlAndPersist(null);
    }

    /**
     * 拉取一次热梗并写入业务库。
     *
     * @param limitOverride 可选；为空则用 {@code meme.crawl.limit}，上限 20
     */
    public MemeCrawlResultVO crawlAndPersist(Integer limitOverride) {
        if (!memeCrawlProperties.isEnabled()) {
            throw new BizException(Result.CODE_ERROR, "热梗采集未启用（meme.crawl.enabled=false）");
        }
        if (!memeCandidateSource.isConfigured()) {
            throw new BizException(Result.CODE_ERROR, "MemeAgent 未配置，无法采集");
        }
        long publisherUserId = memeCrawlProperties.getPublisherUserId();
        if (publisherUserId <= 0L) {
            throw new BizException(Result.CODE_ERROR, "请配置 meme.crawl.publisher-user-id（系统发布者）");
        }

        int configured = Math.max(1, memeCrawlProperties.getLimit());
        int limit = limitOverride == null || limitOverride <= 0
                ? configured
                : Math.max(1, Math.min(limitOverride, 20));

        List<MemeCandidateSource.Candidate> candidates;
        try {
            candidates = memeCandidateSource.fetchCandidates(limit, findProcessedBilibiliVideoIds());
        } catch (Exception e) {
            log.error("调用 MemeAgent /crawl/hot-memes 失败", e);
            throw new BizException(Result.CODE_ERROR, "热梗采集失败，请稍后重试");
        }
        candidates = candidates == null ? List.of() : candidates;

        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (MemeCandidateSource.Candidate candidate : candidates) {
            try {
                Long memeId = transactionTemplate.execute(status -> persistCandidate(candidate, publisherUserId));
                if (memeId != null) {
                    created++;
                    // TransactionTemplate 返回时事务已提交，此时再更新本机内存过滤器，避免回滚数据留在过滤器中。
                    try {
                        memeBloomFilterService.add(memeId);
                    } catch (Exception e) {
                        log.warn("采集梗已入库，但加入布隆过滤器失败 memeId={}", memeId, e);
                    }
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("入库热梗候选失败 title={}", candidate != null ? candidate.title() : null, e);
            }
        }

        MemeCrawlResultVO vo = new MemeCrawlResultVO();
        vo.setFetched(candidates.size());
        vo.setCreated(created);
        vo.setSkipped(skipped);
        vo.setFailed(failed);
        log.info("热梗采集完成 fetched={} created={} skipped={} failed={}",
                vo.getFetched(), created, skipped, failed);
        return vo;
    }

    /** 查询已保存的 B 站视频出处，供本次采集跳过已处理 BVID。 */
    private List<String> findProcessedBilibiliVideoIds() {
        return memeResourceMapper.selectList(new LambdaQueryWrapper<MemeResource>()
                        .select(MemeResource::getResourceUrl)
                        .eq(MemeResource::getResourceType, MemeResourceType.LINK.getCode())
                        .likeRight(MemeResource::getResourceUrl, BILIBILI_VIDEO_URL_PREFIX)
                        .orderByDesc(MemeResource::getId)
                        .last("LIMIT 10000"))
                .stream()
                .map(MemeResource::getResourceUrl)
                .map(MemeCrawlService::extractBilibiliVideoId)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(10000)
                .toList();
    }

    /** 从规范或带查询参数的 B 站视频 URL 中提取原始 BVID。 */
    private static String extractBilibiliVideoId(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        Matcher matcher = BILIBILI_VIDEO_ID_PATTERN.matcher(url.trim());
        if (!matcher.find()) {
            return null;
        }
        return "BV" + matcher.group(1).substring(2);
    }

    private Long persistCandidate(MemeCandidateSource.Candidate candidate, long publisherUserId) {
        if (candidate == null) {
            return null;
        }
        String sourceUrl = truncate(candidate.sourceUrl(), MAX_URL);
        String title = truncate(candidate.title(), MAX_NAME);
        if (!StringUtils.hasText(sourceUrl) || !StringUtils.hasText(title)) {
            return null;
        }
        if (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://")) {
            return null;
        }

        Long exists = memeResourceMapper.selectCount(new LambdaQueryWrapper<MemeResource>()
                .eq(MemeResource::getResourceUrl, sourceUrl)
                .eq(MemeResource::getStatus, RESOURCE_STATUS_ACTIVE));
        if (exists != null && exists > 0) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        Meme meme = new Meme();
        meme.setName(title);
        String intro = truncate(candidate.introduction(), MAX_INTRO);
        if (!StringUtils.hasText(intro)) {
            intro = "来自网络热搜实时采集，详见原文链接。";
        }
        meme.setIntroduction(intro);
        String image = truncate(candidate.imageUrl(), MAX_URL);
        meme.setImage(StringUtils.hasText(image) ? image : "");
        meme.setPageViews(0);
        meme.setLikes(0);
        meme.setComments(0);
        meme.setStatus(MemeVisibilitySupport.STATUS_NORMAL);
        meme.setReleaseTime(now);
        meme.setUpdateTime(now);
        meme.setUserId(publisherUserId);
        memeMapper.insert(meme);

        MemeResource resource = new MemeResource();
        resource.setMemeId(meme.getId());
        resource.setResourceUrl(sourceUrl);
        resource.setResourceType(MemeResourceType.LINK.getCode());
        String linkTitle = sourceUrl.contains("bilibili.com")
                ? truncate("B站原视频 · " + title, 128)
                : truncate(title, 128);
        resource.setTitle(linkTitle);
        resource.setSortOrder(0);
        resource.setStatus(RESOURCE_STATUS_ACTIVE);
        resource.setCreateTime(now);
        memeResourceMapper.insert(resource);

        String detailUrl = truncate(candidate.detailUrl(), MAX_URL);
        if (StringUtils.hasText(detailUrl)
                && (detailUrl.startsWith("http://") || detailUrl.startsWith("https://"))
                && !detailUrl.equals(sourceUrl)) {
            Long detailExists = memeResourceMapper.selectCount(new LambdaQueryWrapper<MemeResource>()
                    .eq(MemeResource::getResourceUrl, detailUrl)
                    .eq(MemeResource::getStatus, RESOURCE_STATUS_ACTIVE));
            if (detailExists == null || detailExists == 0L) {
                MemeResource detail = new MemeResource();
        detail.setMemeId(meme.getId());
                detail.setResourceUrl(detailUrl);
                detail.setResourceType(MemeResourceType.LINK.getCode());
                detail.setTitle(truncate("补充详情 · " + title, 128));
                detail.setSortOrder(1);
                detail.setStatus(RESOURCE_STATUS_ACTIVE);
                detail.setCreateTime(now);
                memeResourceMapper.insert(detail);
            }
        }

        memeIndexEventPort.requestUpsert(meme.getId());
        return meme.getId();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
