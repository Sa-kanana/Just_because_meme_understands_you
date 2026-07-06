package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.FavoriteMoveDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteReorderDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeFavoriteRequestDTO;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteService;
import com.sakana.just_because_meme_understands_you.vo.FavoriteMoveResultVO;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userFavoriteService.addFavorite(userId, request));
    }

    /**
     * 取消收藏梗（需登录）
     * DELETE /favorites/{memeId}
     */
    @DeleteMapping("/favorites/{memeId}")
    public Result<Void> removeFavorite(@PathVariable("memeId") String memeId,
                                       HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        userFavoriteService.removeFavorite(userId, AuthContext.parseLongId(memeId, "memeId"));
        return Result.success();
    }

    /**
     * 查询当前用户是否已收藏该梗（需登录）
     * GET /favorites/{memeId}/status
     */
    @GetMapping("/favorites/{memeId}/status")
    public Result<MemeFavoriteStatusVO> favoriteStatus(@PathVariable("memeId") String memeId,
                                                       HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userFavoriteService.getFavoriteStatus(userId, AuthContext.parseLongId(memeId, "memeId")));
    }

    /**
     * 单条移动到目标夹（需登录）
     * PUT /user/me/favorites/{memeId}/move?folderId=0
     */
    @PutMapping("/user/me/favorites/{memeId}/move")
    public Result<MemeFavoriteVO> moveFavorite(@PathVariable("memeId") String memeId,
                                               HttpServletRequest httpServletRequest,
                                               @org.springframework.web.bind.annotation.RequestParam("folderId") Long folderId) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userFavoriteService.moveFavorite(userId, AuthContext.parseLongId(memeId, "memeId"), folderId));
    }

    /**
     * 批量移动到目标夹（需登录）
     * PUT /user/me/favorites/move
     */
    @PutMapping("/user/me/favorites/move")
    public Result<FavoriteMoveResultVO> batchMove(@RequestBody FavoriteMoveDTO request,
                                                  HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userFavoriteService.batchMoveFavorites(userId, request));
    }

    /**
     * 夹内排序（需登录）
     * PUT /user/me/favorites/reorder
     */
    @PutMapping("/user/me/favorites/reorder")
    public Result<Void> reorder(@RequestBody FavoriteReorderDTO request,
                                HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        userFavoriteService.reorderFavorites(userId, request);
        return Result.success();
    }
}
