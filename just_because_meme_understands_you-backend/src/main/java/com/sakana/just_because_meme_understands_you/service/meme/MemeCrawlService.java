package com.sakana.just_because_meme_understands_you.service.meme;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.MemeResourceType;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentCrawlRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentCrawlResponseDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.service.ai.IAiIngestService;
import com.sakana.just_because_meme_understands_you.service.ai.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.service.meme.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.vo.MemeCrawlResultVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 热梗采集：Agent（B站理解 + Firecrawl 细节）→ MySQL 入库（SSOT）→ 异步灌 pgvector。
 */
@Slf4j
@Service
public class MemeCrawlService {

    private static final int RESOURCE_STATUS_ACTIVE = 1;
    private static final int MAX_NAME = 80;
    /** 与 meme.introduction varchar(255) 对齐 */
    private static final int MAX_INTRO = 255;
    private static final int MAX_URL = 500;

    @Resource
    private MemeAgentProperties memeAgentProperties;

    @Resource
    private MemeAgentClient memeAgentClient;

    @Resource
    private MemeMapper memeMapper;

    @Resource
    private MemeResourceMapper memeResourceMapper;

    @Resource
    private IAiIngestService aiIngestService;

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
        MemeAgentProperties.Crawl crawl = memeAgentProperties.getCrawl();
        if (crawl == null || !crawl.isEnabled()) {
            throw new BizException(Result.CODE_ERROR, "热梗采集未启用（meme.crawl.enabled=false）");
        }
        if (!memeAgentClient.isConfigured()) {
            throw new BizException(Result.CODE_ERROR, "MemeAgent 未配置，无法采集");
        }
        long publisherUserId = crawl.getPublisherUserId();
        if (publisherUserId <= 0L) {
            throw new BizException(Result.CODE_ERROR, "请配置 meme.crawl.publisher-user-id（系统发布者）");
        }

        int configured = Math.max(1, crawl.getLimit());
        int limit = limitOverride == null || limitOverride <= 0
                ? configured
                : Math.max(1, Math.min(limitOverride, 20));

        MemeAgentCrawlRequestDTO request = MemeAgentCrawlRequestDTO.builder()
                .limit(limit)
                .includeMarkdown(true)
                .build();

        MemeAgentCrawlResponseDTO response;
        try {
            response = memeAgentClient.crawlHotMemes(request).block();
        } catch (Exception e) {
            log.error("调用 MemeAgent /crawl/hot-memes 失败", e);
            throw new BizException(Result.CODE_ERROR, "热梗采集失败，请稍后重试");
        }
        if (response == null || response.getCandidates() == null) {
            MemeCrawlResultVO empty = new MemeCrawlResultVO();
            empty.setFetched(0);
            empty.setCreated(0);
            empty.setSkipped(0);
            empty.setFailed(0);
            return empty;
        }

        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (MemeAgentCrawlResponseDTO.Candidate candidate : response.getCandidates()) {
            try {
                Boolean inserted = transactionTemplate.execute(status -> persistCandidate(candidate, publisherUserId));
                if (Boolean.TRUE.equals(inserted)) {
                    created++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("入库热梗候选失败 title={}", candidate != null ? candidate.getTitle() : null, e);
            }
        }

        MemeCrawlResultVO vo = new MemeCrawlResultVO();
        vo.setFetched(response.getCandidates().size());
        vo.setCreated(created);
        vo.setSkipped(skipped);
        vo.setFailed(failed);
        log.info("热梗采集完成 fetched={} created={} skipped={} failed={}",
                vo.getFetched(), created, skipped, failed);
        return vo;
    }

    private boolean persistCandidate(MemeAgentCrawlResponseDTO.Candidate candidate, long publisherUserId) {
        if (candidate == null) {
            return false;
        }
        String sourceUrl = truncate(candidate.getSourceUrl(), MAX_URL);
        String title = truncate(candidate.getTitle(), MAX_NAME);
        if (!StringUtils.hasText(sourceUrl) || !StringUtils.hasText(title)) {
            return false;
        }
        if (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://")) {
            return false;
        }

        Long exists = memeResourceMapper.selectCount(new LambdaQueryWrapper<MemeResource>()
                .eq(MemeResource::getResourceUrl, sourceUrl)
                .eq(MemeResource::getStatus, RESOURCE_STATUS_ACTIVE));
        if (exists != null && exists > 0) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        Meme meme = new Meme();
        meme.setName(title);
        String intro = truncate(candidate.getIntroduction(), MAX_INTRO);
        if (!StringUtils.hasText(intro)) {
            intro = "来自网络热搜实时采集，详见原文链接。";
        }
        meme.setIntroduction(intro);
        String image = truncate(candidate.getImageUrl(), MAX_URL);
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
        resource.setMemeId(meme.getId().longValue());
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

        String detailUrl = truncate(candidate.getDetailUrl(), MAX_URL);
        if (StringUtils.hasText(detailUrl)
                && (detailUrl.startsWith("http://") || detailUrl.startsWith("https://"))
                && !detailUrl.equals(sourceUrl)) {
            Long detailExists = memeResourceMapper.selectCount(new LambdaQueryWrapper<MemeResource>()
                    .eq(MemeResource::getResourceUrl, detailUrl)
                    .eq(MemeResource::getStatus, RESOURCE_STATUS_ACTIVE));
            if (detailExists == null || detailExists == 0L) {
                MemeResource detail = new MemeResource();
                detail.setMemeId(meme.getId().longValue());
                detail.setResourceUrl(detailUrl);
                detail.setResourceType(MemeResourceType.LINK.getCode());
                detail.setTitle(truncate("补充详情 · " + title, 128));
                detail.setSortOrder(1);
                detail.setStatus(RESOURCE_STATUS_ACTIVE);
                detail.setCreateTime(now);
                memeResourceMapper.insert(detail);
            }
        }

        aiIngestService.syncMeme(meme.getId().longValue());
        return true;
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
