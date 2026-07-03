package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.MemeFavoriteRequestDTO;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteService;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemeFavoriteController {

    @Resource
    private IUserFavoriteService userFavoriteService;

    /**
     * 收藏梗（需登录）
     * POST /detail/favorites
     */
    @PostMapping("/detail/favorites")
    public Result<MemeFavoriteVO> addFavorite(@RequestBody MemeFavoriteRequestDTO request,
                                              HttpServletRequest httpServletRequest) {
        Long userId = requireCurrentUserId(httpServletRequest);
        return Result.success(userFavoriteService.addFavorite(userId, request));
    }

    /**
     * 取消收藏梗（需登录）
     * DELETE /favorites/{memeId}
     */
    @DeleteMapping("/favorites/{memeId}")
    public Result<Void> removeFavorite(@PathVariable("memeId") String memeId,
                                       HttpServletRequest httpServletRequest) {
        Long userId = requireCurrentUserId(httpServletRequest);
        userFavoriteService.removeFavorite(userId, parseMemeId(memeId));
        return Result.success();
    }

    /**
     * 查询当前用户是否已收藏该梗（需登录）
     * GET /favorites/{memeId}/status
     */
    @GetMapping("/favorites/{memeId}/status")
    public Result<MemeFavoriteStatusVO> favoriteStatus(@PathVariable("memeId") String memeId,
                                                       HttpServletRequest httpServletRequest) {
        Long userId = requireCurrentUserId(httpServletRequest);
        MemeFavoriteStatusVO vo = new MemeFavoriteStatusVO();
        vo.setFavorited(userFavoriteService.isFavorited(userId, parseMemeId(memeId)));
        return Result.success(vo);
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

    private Long parseMemeId(String memeId) {
        if (!StringUtils.hasText(memeId)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        try {
            long parsed = Long.parseLong(memeId.trim());
            if (parsed <= 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 格式错误");
        }
    }
}
