package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;
import com.sakana.just_because_meme_understands_you.modules.knowledge.application.MemeKnowledgeService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/v1/memes/{memeId}/knowledge")
public class MemeKnowledgeController {
    @Resource private MemeKnowledgeService service;

    @GetMapping
    public Result<MemeKnowledgeService.CardView> card(@PathVariable String memeId) {
        return Result.success(service.publicCard(RequestIdParser.parseLongId(memeId, "memeId")));
    }

    @PostMapping("/corrections")
    public Result<Long> submit(@PathVariable String memeId, @RequestBody Map<String, Object> changes,
                               HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(service.submit(RequestIdParser.parseLongId(memeId, "memeId"), userId, changes));
    }
}
