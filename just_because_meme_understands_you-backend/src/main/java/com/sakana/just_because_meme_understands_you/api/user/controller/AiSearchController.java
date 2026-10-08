package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.api.user.dto.AiSearchStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AiStreamTaskVO;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiStreamTaskService;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskSnapshot;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/v1/ai")
public class AiSearchController {

    @Resource
    private IAiStreamTaskService aiStreamTaskService;

    /**
     * 创建后台 AI 搜索任务；请求幂等时返回已有任务。
     * POST /v1/ai/searches
     */
    @PostMapping(value = "/searches", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public com.sakana.just_because_meme_understands_you.common.response.Result<AiStreamTaskVO> createSearch(
            @Valid @RequestBody AiSearchStreamRequestDTO request,
            HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        log.info("AI search task requested userId={} sessionId={}", userId, request.getSessionId());
        return com.sakana.just_because_meme_understands_you.common.response.Result.success(
                AiStreamTaskVO.from(aiStreamTaskService.submit(userId, request)));
    }

    /** 查询当前用户最近一个可恢复的 AI 搜索任务。 */
    @GetMapping(value = "/searches/current", produces = MediaType.APPLICATION_JSON_VALUE)
    public com.sakana.just_because_meme_understands_you.common.response.Result<AiStreamTaskVO> getCurrent(
            HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return com.sakana.just_because_meme_understands_you.common.response.Result.success(
                aiStreamTaskService.getCurrent(userId).map(AiStreamTaskVO::from).orElse(null));
    }

    /** 按事件游标订阅任务事件，断线后可从最后确认的 ID 继续回放。 */
    @GetMapping(value = "/searches/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> events(
            @PathVariable("taskId") String taskId,
            @RequestParam(name = "after", defaultValue = "0") long afterEventId,
            HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return aiStreamTaskService.events(userId, taskId, afterEventId);
    }

    /** 显式取消当前用户拥有的运行中任务。 */
    @PostMapping(value = "/searches/{taskId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public com.sakana.just_because_meme_understands_you.common.response.Result<AiStreamTaskVO> cancel(
            @PathVariable("taskId") String taskId,
            HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        AiStreamTaskSnapshot cancelled = aiStreamTaskService.cancel(userId, taskId);
        return com.sakana.just_because_meme_understands_you.common.response.Result.success(
                AiStreamTaskVO.from(cancelled));
    }
}
