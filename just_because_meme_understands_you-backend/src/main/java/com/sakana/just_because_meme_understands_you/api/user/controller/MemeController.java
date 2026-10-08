package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.meme.application.IMemeTagService;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemeDeleteService;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemePublishService;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCreateResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeDeleteResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemePurgeResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeRestoreResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

/**
 * 梗发布接口
 *
 * @author sakana
 */
@RestController
@RequestMapping("/v1")
public class MemeController {

    @Resource
    private MemePublishService memePublishService;

    @Resource
    private MemeDeleteService memeDeleteService;

    @Resource
    private IMemeTagService memeTagService;

    /**
     * 获取所有可用标签，供发布梗时选择
     * GET /v1/tags
     */
    @GetMapping("/tags")
    public Result<List<MemeTagVO>> listTags() {
        return Result.success(memeTagService.listTagOptions());
    }

    /**
     * 发布梗（需登录）
     * POST /v1/memes
     * 图片资源采用两阶段法：前端先调 /v1/uploads/policies 直传 OSS 拿到 URL，再随表单提交。
     */
    @PostMapping("/memes")
    public Result<MemeCreateResponseVO> publish(@RequestBody MemeCreateRequestDTO request,
                                                 HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(memePublishService.publish(userId, request));
    }

    /**
     * 删除自己发布的梗（需登录，软删除）
     * DELETE /v1/memes/{memeId}
     */
    @DeleteMapping("/memes/{memeId}")
    public Result<MemeDeleteResponseVO> deleteOwnMeme(@PathVariable("memeId") String memeId,
                                                       HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(memeDeleteService.deleteOwnMeme(userId, RequestIdParser.parseLongId(memeId, "memeId")));
    }

    /**
     * 恢复已下架的梗（需登录，重新进入审核）
     * POST /v1/memes/{memeId}/restorations
     */
    @PostMapping("/memes/{memeId}/restorations")
    public Result<MemeRestoreResponseVO> restoreOwnMeme(@PathVariable("memeId") String memeId,
                                                        HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(memeDeleteService.restoreOwnMeme(userId, RequestIdParser.parseLongId(memeId, "memeId")));
    }

    /**
     * 彻底删除已下架的梗（需登录）
     * DELETE /v1/memes/{memeId}?permanent=true
     */
    @DeleteMapping(value = "/memes/{memeId}", params = "permanent=true")
    public Result<MemePurgeResponseVO> purgeOwnMeme(@PathVariable("memeId") String memeId,
                                                      HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(memeDeleteService.purgeOwnMeme(userId, RequestIdParser.parseLongId(memeId, "memeId")));
    }
}
