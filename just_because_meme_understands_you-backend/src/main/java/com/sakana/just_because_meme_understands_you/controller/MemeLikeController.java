package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.MemeLikeRequestDTO;
import com.sakana.just_because_meme_understands_you.service.user.IUserLikeService;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeBatchStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeDeleteVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemeLikeController {

    @Resource
    private IUserLikeService userLikeService;

    /**
     * 点赞梗（需登录）
     * POST /detail/likes
     */
    @PostMapping("/detail/likes")
    public Result<MemeLikeVO> addLike(@RequestBody MemeLikeRequestDTO request,
                                      HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userLikeService.addLike(userId, request));
    }

    /**
     * 取消点赞（需登录）
     * DELETE /likes/{memeId}
     */
    @DeleteMapping("/likes/{memeId}")
    public Result<MemeLikeDeleteVO> removeLike(@PathVariable("memeId") String memeId,
                                             HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userLikeService.removeLike(userId, AuthContext.parseLongId(memeId, "memeId")));
    }

    /**
     * 查询当前用户是否已点赞（需登录）
     * GET /likes/{memeId}/status
     */
    @GetMapping("/likes/{memeId}/status")
    public Result<MemeLikeStatusVO> likeStatus(@PathVariable("memeId") String memeId,
                                             HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userLikeService.getLikeStatus(userId, AuthContext.parseLongId(memeId, "memeId")));
    }

    /**
     * 批量查询点赞状态（需登录）
     * GET /likes/status?memeIds=1,2,3
     */
    @GetMapping("/likes/status")
    public Result<MemeLikeBatchStatusVO> batchLikeStatus(@RequestParam(value = "memeIds", required = false) String memeIds,
                                                         HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userLikeService.batchLikeStatus(userId, memeIds));
    }
}
