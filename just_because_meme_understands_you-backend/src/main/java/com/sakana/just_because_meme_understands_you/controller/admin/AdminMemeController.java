package com.sakana.just_because_meme_understands_you.controller.admin;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.service.admin.IAdminMemeService;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminMemeActionVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminMemeDetailVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminMemeListItemVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/memes")
public class AdminMemeController {

    @Resource
    private IAdminMemeService adminMemeService;

    @GetMapping
    public Result<PageVO<AdminMemeListItemVO>> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "userId", required = false) String userId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long uid = null;
        if (userId != null && !userId.isBlank()) {
            uid = AuthContext.parseUserId(userId);
        }
        return Result.success(adminMemeService.page(page, size, status, keyword, uid));
    }

    @GetMapping("/{id}")
    public Result<AdminMemeDetailVO> detail(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long memeId = AuthContext.parseLongId(id, "memeId");
        return Result.success(adminMemeService.detail(memeId));
    }

    @PostMapping("/{id}/approve")
    public Result<AdminMemeActionVO> approve(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long memeId = AuthContext.parseLongId(id, "memeId");
        return Result.success(adminMemeService.approve(memeId));
    }

    @PostMapping("/{id}/reject")
    public Result<AdminMemeActionVO> reject(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long memeId = AuthContext.parseLongId(id, "memeId");
        return Result.success(adminMemeService.reject(memeId));
    }

    @PostMapping("/{id}/offline")
    public Result<AdminMemeActionVO> offline(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long memeId = AuthContext.parseLongId(id, "memeId");
        return Result.success(adminMemeService.offline(memeId));
    }

    @PostMapping("/{id}/online")
    public Result<AdminMemeActionVO> online(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long memeId = AuthContext.parseLongId(id, "memeId");
        return Result.success(adminMemeService.online(memeId));
    }
}
