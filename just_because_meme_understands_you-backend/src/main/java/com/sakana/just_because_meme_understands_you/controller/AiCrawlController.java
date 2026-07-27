package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.service.meme.MemeCrawlService;
import com.sakana.just_because_meme_understands_you.vo.MemeCrawlResultVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 热梗采集运维入口（管理员）。
 */
@RestController
@RequestMapping("/ai/crawl")
public class AiCrawlController {

    @Resource
    private MemeCrawlService memeCrawlService;

    @Resource
    private MemeAgentProperties memeAgentProperties;

    /**
     * 手动触发一次 Firecrawl 热梗采集并入库。
     * POST /ai/crawl/trigger
     */
    @PostMapping("/trigger")
    public Result<MemeCrawlResultVO> trigger(HttpServletRequest request) {
        requireOpsAdmin(request);
        return Result.success(memeCrawlService.crawlAndPersist());
    }

    private void requireOpsAdmin(HttpServletRequest request) {
        MemeAgentProperties.Ops ops = memeAgentProperties.getOps();
        if (ops != null && !ops.isRequireAdmin()) {
            AuthContext.requireCurrentUserId(request);
            return;
        }
        AuthContext.requireAdmin(request, ops == null ? null : ops.getAdminUserIds());
    }
}
