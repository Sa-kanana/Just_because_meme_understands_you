package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminMemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminMemeStatusRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminMemeService;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeActionVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeDetailVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeListItemVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCreateResponseVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequestMapping("/admin/v1/memes")
public class AdminMemeController {

    @Resource
    private IAdminMemeService adminMemeService;

    /** 创建梗并将其提交到现有审核流程。 */
    @PostMapping
    public Result<MemeCreateResponseVO> create(
            @Valid @RequestBody AdminMemeCreateRequestDTO body,
            HttpServletRequest request) {
        Long adminUserId = AdminAuthSupport.requireAdmin(request);
        return Result.success(adminMemeService.create(adminUserId, body));
    }

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
            uid = RequestIdParser.parseLong(userId, "userId");
        }
        return Result.success(adminMemeService.page(page, size, status, keyword, uid));
    }

    @GetMapping("/{id}")
    public Result<AdminMemeDetailVO> detail(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long memeId = RequestIdParser.parseLongId(id, "memeId");
        return Result.success(adminMemeService.detail(memeId));
    }

    @PatchMapping("/{id}")
    public Result<AdminMemeActionVO> updateStatus(@PathVariable("id") String id,
                                                  @Valid @RequestBody AdminMemeStatusRequestDTO body,
                                                  HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long memeId = RequestIdParser.parseLongId(id, "memeId");
        AdminMemeActionVO updated = switch (body.getStatus()) {
            case "APPROVED" -> adminMemeService.approve(memeId);
            case "REJECTED" -> adminMemeService.reject(memeId);
            case "OFFLINE" -> adminMemeService.offline(memeId);
            case "ONLINE" -> adminMemeService.online(memeId);
            default -> throw new com.sakana.just_because_meme_understands_you.common.exception.BizException(
                    Result.CODE_BAD_REQUEST, "梗状态不受支持");
        };
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<AdminMemeActionVO> delete(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long memeId = RequestIdParser.parseLongId(id, "memeId");
        return Result.success(adminMemeService.delete(memeId));
    }
}
