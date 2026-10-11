package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeCommentCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.comment.application.IMemeCommentService;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentAnchorVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentCreateResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentPageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeReplyCommentVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@RestController
@RequestMapping("/v1")
public class MemeCommentController {

    @Resource
    private IMemeCommentService memeCommentService;

    @GetMapping("/memes/{memeId}/comments")
    public Result<MemeCommentPageVO> pageRootComments(@PathVariable("memeId") String memeId,
                                                      @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                      @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
                                                      @RequestParam(value = "sortType", required = false, defaultValue = "new") String sortType,
                                                      HttpServletRequest httpServletRequest) {
        return Result.success(memeCommentService.pageRootComments(
                RequestIdParser.parseLongId(memeId, "memeId"),
                page,
                size,
                sortType,
                AuthContext.currentUserId(httpServletRequest)));
    }

    @GetMapping("/comments/{rootId}/replies")
    public Result<List<MemeReplyCommentVO>> pageReplies(@PathVariable("rootId") String rootId,
                                                        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                        @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
                                                        HttpServletRequest httpServletRequest) {
        return Result.success(memeCommentService.pageReplies(
                RequestIdParser.parseLongId(rootId, "rootId"),
                page,
                size,
                AuthContext.currentUserId(httpServletRequest)));
    }

    /**
     * 评论定位（消息跳转）
     * GET /comments/{commentId}/context
     */
    @GetMapping("/comments/{commentId}/context")
    public Result<MemeCommentAnchorVO> locateComment(@PathVariable("commentId") String commentId,
                                                     HttpServletRequest httpServletRequest) {
        return Result.success(memeCommentService.locateComment(
                RequestIdParser.parseLongId(commentId, "commentId"),
                AuthContext.currentUserId(httpServletRequest)));
    }

    /**
     * 发表评论（根评论 / 回复子评论，需登录）
     * POST /memes/{memeId}/comments
     */
    @PostMapping("/memes/{memeId}/comments")
    public Result<MemeCommentCreateResponseVO> createComment(@PathVariable("memeId") String memeId,
                                                              @RequestBody MemeCommentCreateRequestDTO request,
                                                              HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        request.setMemeId(RequestIdParser.parseLongId(memeId, "memeId"));
        return Result.success(memeCommentService.createComment(userId, request));
    }

    /** 删除当前用户自己的评论。 */
    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable("commentId") String commentId,
                                      HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        memeCommentService.deleteComment(userId, RequestIdParser.parseLongId(commentId, "commentId"));
        return Result.success();
    }
}
