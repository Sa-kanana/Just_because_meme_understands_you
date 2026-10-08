package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.api.user.dto.FavoriteMoveDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.FavoriteReorderDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeFavoriteRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.user.application.favorite.IUserFavoriteService;
import com.sakana.just_because_meme_understands_you.api.user.vo.FavoriteMoveResultVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeFavoriteStatusVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeFavoriteVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@RestController
@RequestMapping("/v1/users/me/favorites")
public class MemeFavoriteController {

    @Resource
    private IUserFavoriteService userFavoriteService;

    /**
     * 收藏梗（需登录）
     * PUT /v1/users/me/favorites/{memeId}
     */
    @PutMapping("/{memeId}")
    public Result<MemeFavoriteVO> addFavorite(@PathVariable("memeId") String memeId,
                                              @RequestBody MemeFavoriteRequestDTO request,
                                              HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        request.setMemeId(RequestIdParser.parseLongId(memeId, "memeId"));
        return Result.success(userFavoriteService.addFavorite(userId, request));
    }

    /**
     * 取消收藏梗（需登录）
     * DELETE /v1/users/me/favorites/{memeId}
     */
    @DeleteMapping("/{memeId}")
    public Result<Void> removeFavorite(@PathVariable("memeId") String memeId,
                                       HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        userFavoriteService.removeFavorite(userId, RequestIdParser.parseLongId(memeId, "memeId"));
        return Result.success();
    }

    /**
     * 查询当前用户是否已收藏该梗（需登录）
     * GET /v1/users/me/favorites/{memeId}
     */
    @GetMapping("/{memeId}")
    public Result<MemeFavoriteStatusVO> favoriteStatus(@PathVariable("memeId") String memeId,
                                                       HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userFavoriteService.getFavoriteStatus(userId, RequestIdParser.parseLongId(memeId, "memeId")));
    }

    /**
     * 单条移动到目标夹（需登录）
     * PATCH /v1/users/me/favorites/{memeId}，请求体包含 targetFolderId
     */
    @PatchMapping("/{memeId}")
    public Result<MemeFavoriteVO> moveFavorite(@PathVariable("memeId") String memeId,
                                               @RequestBody FavoriteMoveDTO request,
                                               HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userFavoriteService.moveFavorite(userId, RequestIdParser.parseLongId(memeId, "memeId"), request.getTargetFolderId()));
    }

    /**
     * 批量移动到目标夹（需登录）
     * PATCH /v1/users/me/favorites
     */
    @PatchMapping
    public Result<FavoriteMoveResultVO> batchMove(@RequestBody FavoriteMoveDTO request,
                                                  HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userFavoriteService.batchMoveFavorites(userId, request));
    }

    /**
     * 夹内排序（需登录）
     * PUT /v1/users/me/favorites/order
     */
    @PutMapping("/order")
    public Result<Void> reorder(@RequestBody FavoriteReorderDTO request,
                                HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        userFavoriteService.reorderFavorites(userId, request);
        return Result.success();
    }
}
