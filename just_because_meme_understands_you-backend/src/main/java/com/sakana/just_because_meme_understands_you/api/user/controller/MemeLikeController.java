package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeLikeRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.user.application.like.IUserLikeService;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeBatchStatusVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeDeleteVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeStatusVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@RestController
@RequestMapping("/v1/users/me/liked-memes")
public class MemeLikeController {

    @Resource
    private IUserLikeService userLikeService;

    /**
     * 点赞梗（需登录）
     * PUT /v1/users/me/liked-memes/{memeId}
     */
    @PutMapping("/{memeId}")
    public Result<MemeLikeVO> addLike(@PathVariable("memeId") String memeId,
                                      HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        MemeLikeRequestDTO request = new MemeLikeRequestDTO();
        request.setMemeId(RequestIdParser.parseLongId(memeId, "memeId"));
        return Result.success(userLikeService.addLike(userId, request));
    }

    /**
     * 取消点赞（需登录）
     * DELETE /v1/users/me/liked-memes/{memeId}
     */
    @DeleteMapping("/{memeId}")
    public Result<MemeLikeDeleteVO> removeLike(@PathVariable("memeId") String memeId,
                                             HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userLikeService.removeLike(userId, RequestIdParser.parseLongId(memeId, "memeId")));
    }

    /**
     * 查询当前用户是否已点赞（需登录）
     * GET /v1/users/me/liked-memes/{memeId}
     */
    @GetMapping("/{memeId}")
    public Result<MemeLikeStatusVO> likeStatus(@PathVariable("memeId") String memeId,
                                             HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userLikeService.getLikeStatus(userId, RequestIdParser.parseLongId(memeId, "memeId")));
    }

    /**
     * 批量查询点赞状态（需登录）
     * GET /v1/users/me/liked-memes?memeIds=1,2,3
     */
    @GetMapping
    public Result<MemeLikeBatchStatusVO> batchLikeStatus(@RequestParam(value = "memeIds", required = false) String memeIds,
                                                         HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userLikeService.batchLikeStatus(userId, memeIds));
    }
}
