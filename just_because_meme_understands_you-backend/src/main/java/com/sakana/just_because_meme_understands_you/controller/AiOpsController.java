package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.service.ai.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.vo.AiOpsStatusVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 运维状态（管理员）。
 */
@RestController
@RequestMapping("/ai/ops")
public class AiOpsController {

    @Resource
    private MemeAgentClient memeAgentClient;

    @Resource
    private MemeAgentProperties memeAgentProperties;

    /**
     * 运维面板：Agent 健康与开关配置。
     * GET /ai/ops/status
     */
    @GetMapping("/status")
    public Result<AiOpsStatusVO> status(HttpServletRequest request) {
        requireOpsAdmin(request);

        MemeAgentProperties.Agent agent = memeAgentProperties.getAgent();
        MemeAgentProperties.Crawl crawl = memeAgentProperties.getCrawl();
        MemeAgentProperties.Ai ai = memeAgentProperties.getAi();

        AiOpsStatusVO vo = new AiOpsStatusVO();
        vo.setAgentConfigured(memeAgentClient.isConfigured());
        vo.setAgentBaseUrl(agent != null ? agent.getBaseUrl() : null);
        vo.setCrawlEnabled(crawl != null && crawl.isEnabled());
        vo.setCrawlScheduleEnabled(crawl != null && crawl.isScheduleEnabled());
        vo.setCrawlDefaultLimit(crawl != null ? Math.max(1, crawl.getLimit()) : 5);
        vo.setBackfillEnabled(ai == null || ai.isBackfillEnabled());

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
        MemeAgentProperties.Ops ops = memeAgentProperties.getOps();
        if (ops != null && !ops.isRequireAdmin()) {
            AuthContext.requireCurrentUserId(request);
            return;
        }
        AuthContext.requireAdmin(request, ops == null ? null : ops.getAdminUserIds());
    }
}
