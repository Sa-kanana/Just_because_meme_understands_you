package com.sakana.just_because_meme_understands_you.controller.admin;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.admin.AdminKnowledgeSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.service.admin.IAdminKnowledgeService;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminKnowledgeVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/knowledge")
public class AdminKnowledgeController {

    @Resource
    private IAdminKnowledgeService adminKnowledgeService;

    @GetMapping
    public Result<PageVO<AdminKnowledgeVO>> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(adminKnowledgeService.page(page, size, keyword, category));
    }

    @GetMapping("/{id}")
    public Result<AdminKnowledgeVO> detail(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long docId = AuthContext.parseLongId(id, "id");
        return Result.success(adminKnowledgeService.detail(docId));
    }

    @PostMapping
    public Result<AdminKnowledgeVO> create(
            @Valid @RequestBody AdminKnowledgeSaveRequestDTO body,
            HttpServletRequest request) {
        Long adminId = AdminAuthSupport.requireAdmin(request);
        return Result.success(adminKnowledgeService.create(adminId, body));
    }

    /**
     * 上传 Markdown / PDF，服务端提取文本后入库并触发向量灌库。
     */
    @PostMapping("/upload")
    public Result<AdminKnowledgeVO> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            HttpServletRequest request) {
        Long adminId = AdminAuthSupport.requireAdmin(request);
        return Result.success(adminKnowledgeService.createFromFile(
                adminId, file, title, category, parseTagsParam(tags)));
    }

    @PutMapping("/{id}")
    public Result<AdminKnowledgeVO> update(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminKnowledgeSaveRequestDTO body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long docId = AuthContext.parseLongId(id, "id");
        return Result.success(adminKnowledgeService.update(docId, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long docId = AuthContext.parseLongId(id, "id");
        adminKnowledgeService.delete(docId);
        return Result.success();
    }

    @PostMapping("/{id}/reindex")
    public Result<AdminKnowledgeVO> reindex(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long docId = AuthContext.parseLongId(id, "id");
        return Result.success(adminKnowledgeService.reindex(docId));
    }

    private static List<String> parseTagsParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
