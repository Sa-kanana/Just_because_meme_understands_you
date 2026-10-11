package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.comment.application.IUserCommentLikeService;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentLikeBatchStatusVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentLikeVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@RestController
@RequestMapping("/v1")
public class MemeCommentLikeController {

    @Resource
    private IUserCommentLikeService userCommentLikeService;

    /**
     * 评论点赞（需登录）
     * PUT /v1/comments/{commentId}/likes/me
     */
    @PutMapping("/comments/{commentId}/likes/me")
    public Result<MemeCommentLikeVO> addLike(@PathVariable("commentId") String commentId,
                                             HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userCommentLikeService.addLike(userId, RequestIdParser.parseLongId(commentId, "commentId")));
    }

    /**
     * 取消评论点赞（需登录）
     * DELETE /v1/comments/{commentId}/likes/me
     */
    @DeleteMapping("/comments/{commentId}/likes/me")
    public Result<MemeCommentLikeVO> removeLike(@PathVariable("commentId") String commentId,
                                                HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userCommentLikeService.removeLike(userId, RequestIdParser.parseLongId(commentId, "commentId")));
    }

    /**
     * 单条评论点赞状态（可选登录）
     * GET /v1/comments/{commentId}/likes/me
     */
    @GetMapping("/comments/{commentId}/likes/me")
    public Result<MemeCommentLikeVO> likeStatus(@PathVariable("commentId") String commentId,
                                                HttpServletRequest httpServletRequest) {
        return Result.success(userCommentLikeService.getLikeStatus(
                AuthContext.currentUserId(httpServletRequest),
                RequestIdParser.parseLongId(commentId, "commentId")));
    }

    /**
     * 批量评论点赞状态（需登录）
     * GET /v1/comment-like-statuses?commentIds=1,2,3
     */
    @GetMapping("/comment-like-statuses")
    public Result<MemeCommentLikeBatchStatusVO> batchLikeStatus(
            @RequestParam(value = "commentIds", required = false) String commentIds,
            HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userCommentLikeService.batchLikeStatus(userId, commentIds));
    }
}
