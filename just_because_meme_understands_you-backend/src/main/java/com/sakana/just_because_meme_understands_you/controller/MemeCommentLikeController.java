package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.service.comment.IUserCommentLikeService;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentLikeBatchStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentLikeVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemeCommentLikeController {

    @Resource
    private IUserCommentLikeService userCommentLikeService;

    /**
     * 评论点赞（需登录）
     * POST /detail/comments/{commentId}/likes
     */
    @PostMapping("/detail/comments/{commentId}/likes")
    public Result<MemeCommentLikeVO> addLike(@PathVariable("commentId") String commentId,
                                             HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userCommentLikeService.addLike(userId, AuthContext.parseLongId(commentId, "commentId")));
    }

    /**
     * 取消评论点赞（需登录）
     * DELETE /detail/comments/{commentId}/likes
     */
    @DeleteMapping("/detail/comments/{commentId}/likes")
    public Result<MemeCommentLikeVO> removeLike(@PathVariable("commentId") String commentId,
                                                HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userCommentLikeService.removeLike(userId, AuthContext.parseLongId(commentId, "commentId")));
    }

    /**
     * 单条评论点赞状态（可选登录）
     * GET /detail/comments/{commentId}/likes/status
     */
    @GetMapping("/detail/comments/{commentId}/likes/status")
    public Result<MemeCommentLikeVO> likeStatus(@PathVariable("commentId") String commentId,
                                                HttpServletRequest httpServletRequest) {
        return Result.success(userCommentLikeService.getLikeStatus(
                AuthContext.currentUserId(httpServletRequest),
                AuthContext.parseLongId(commentId, "commentId")));
    }

    /**
     * 批量评论点赞状态（需登录）
     * GET /detail/comments/likes/status?commentIds=1,2,3
     */
    @GetMapping("/detail/comments/likes/status")
    public Result<MemeCommentLikeBatchStatusVO> batchLikeStatus(
            @RequestParam(value = "commentIds", required = false) String commentIds,
            HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userCommentLikeService.batchLikeStatus(userId, commentIds));
    }
}
