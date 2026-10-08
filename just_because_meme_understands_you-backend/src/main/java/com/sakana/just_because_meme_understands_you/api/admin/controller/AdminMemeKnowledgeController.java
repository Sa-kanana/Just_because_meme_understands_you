package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;
import com.sakana.just_because_meme_understands_you.modules.knowledge.application.MemeKnowledgeService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/v1/admin/meme-knowledge-corrections")
public class AdminMemeKnowledgeController {
    @Resource private MemeKnowledgeService service;

    @GetMapping
    public Result<PageVO<MemeKnowledgeService.CorrectionView>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(service.page(page, size, status));
    }

    @PatchMapping("/{id}")
    public Result<MemeKnowledgeService.CorrectionView> resolve(@PathVariable String id,
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long adminId = AdminAuthSupport.requireAdmin(request);
        if (body == null || !body.containsKey("decision") || !Set.of("decision", "note").containsAll(body.keySet())
                || !(body.get("decision") instanceof String)
                || (body.get("note") != null && !(body.get("note") instanceof String))) {
            throw new BizException(Result.CODE_BAD_REQUEST, "审核参数不合法");
        }
        return Result.success(service.resolve(RequestIdParser.parseLongId(id, "id"), adminId,
                (String) body.get("decision"), (String) body.get("note")));
    }
}
