package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.MemeCommentCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.service.comment.IMemeCommentService;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentCreateResponseVO;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentPageVO;
import com.sakana.just_because_meme_understands_you.vo.MemeReplyCommentVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MemeCommentController {

    @Resource
    private IMemeCommentService memeCommentService;

    @GetMapping("/detail/{memeId}/comments")
    public Result<MemeCommentPageVO> pageRootComments(@PathVariable("memeId") String memeId,
                                                      @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                      @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
                                                      @RequestParam(value = "sortType", required = false, defaultValue = "new") String sortType) {
        return Result.success(memeCommentService.pageRootComments(parseLongId(memeId, "memeId"), page, size, sortType));
    }

    @GetMapping("/detail/comments/{rootId}/replies")
    public Result<List<MemeReplyCommentVO>> pageReplies(@PathVariable("rootId") String rootId,
                                                        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                        @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        return Result.success(memeCommentService.pageReplies(parseLongId(rootId, "rootId"), page, size));
    }

    /**
     * 发表评论（根评论 / 回复子评论，需登录）
     * POST /detail/comments
     */
    @PostMapping("/detail/comments")
    public Result<MemeCommentCreateResponseVO> createComment(@RequestBody MemeCommentCreateRequestDTO request,
                                                              HttpServletRequest httpServletRequest) {
        Long userId = requireCurrentUserId(httpServletRequest);
        return Result.success(memeCommentService.createComment(userId, request));
    }

    private Long requireCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        try {
            long parsed = Long.parseLong(String.valueOf(userId));
            if (parsed <= 0) {
                throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }

    private Long parseLongId(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 不能为空");
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed <= 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 不合法");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 格式错误");
        }
    }
}
