package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AiOpsStatusVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;
import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;

/**
 * AI 运维状态（管理员）。
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequestMapping("/admin/v1/ai/operations")
public class AiOpsController {

    @Resource
    private MemeAgentClient memeAgentClient;

    @Resource
    private MemeAgentProperties memeAgentProperties;

    @Resource
    private MemeCatalogPort memeCatalogPort;

    /**
     * 运维面板：Agent 健康与开关配置。
     * GET /admin/v1/ai/operations
     */
    @GetMapping
    public Result<AiOpsStatusVO> status(HttpServletRequest request) {
        requireOpsAdmin(request);

        MemeAgentProperties.Agent agent = memeAgentProperties.getAgent();
        MemeCatalogPort.CrawlSettings crawl = memeCatalogPort.crawlSettings();

        AiOpsStatusVO vo = new AiOpsStatusVO();
        vo.setAgentConfigured(memeAgentClient.isConfigured());
        vo.setAgentBaseUrl(agent != null ? agent.getBaseUrl() : null);
        vo.setCrawlEnabled(crawl.enabled());
        vo.setCrawlScheduleEnabled(crawl.scheduleEnabled());
        vo.setCrawlDefaultLimit(Math.max(1, crawl.limit()));
        if (vo.isAgentConfigured()) {
            Map<String, Object> health = memeAgentClient.probeHealth().block();
            if (health != null && !health.isEmpty()) {
                vo.setAgentReachable(true);
                Object service = health.get("service");
                Object env = health.get("env");
                Object vectorDb = health.get("vector_db");
                if (service != null) {
                    vo.setAgentService(String.valueOf(service));
                }
                if (env != null) {
                    vo.setAgentEnv(String.valueOf(env));
                }
                if (vectorDb instanceof Boolean bool) {
                    vo.setVectorDbOk(bool);
                } else if (vectorDb != null && StringUtils.hasText(String.valueOf(vectorDb))) {
                    vo.setVectorDbOk(Boolean.parseBoolean(String.valueOf(vectorDb)));
                }
            } else {
                vo.setAgentReachable(false);
            }
        } else {
            vo.setAgentReachable(false);
        }
        return Result.success(vo);
    }

    private void requireOpsAdmin(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
    }
}
