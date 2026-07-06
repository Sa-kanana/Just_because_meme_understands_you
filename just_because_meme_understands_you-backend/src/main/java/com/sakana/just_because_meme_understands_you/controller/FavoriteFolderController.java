package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderCreateDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderReorderDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderUpdateDTO;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderListVO;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏夹管理（本人）：/user/me/favorite-folders
 * 他人查看公开夹：/user/{userId}/favorite-folders
 */
@RestController
@RequestMapping("/user")
public class FavoriteFolderController {

    @Resource
    private IUserFavoriteFolderService favoriteFolderService;

    /** 本人收藏夹列表（含默认夹） */
    @GetMapping("/me/favorite-folders")
    public Result<FavoriteFolderListVO> myFolders(HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(favoriteFolderService.listMyFolders(userId));
    }

    /** 他人收藏夹列表（仅公开自定义夹）；本人同 /me */
    @GetMapping("/{userId}/favorite-folders")
    public Result<FavoriteFolderListVO> folders(@PathVariable("userId") String userId,
                                                  HttpServletRequest request) {
        Long targetUserId = AuthContext.parseUserId(userId);
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(favoriteFolderService.listFolders(targetUserId, currentUserId));
    }

    @PostMapping("/me/favorite-folders")
    public Result<FavoriteFolderVO> create(@RequestBody FavoriteFolderCreateDTO request,
                                           HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(favoriteFolderService.createFolder(userId, request));
    }

    @PutMapping("/me/favorite-folders/{folderId}")
    public Result<Void> update(@PathVariable("folderId") String folderId,
                               @RequestBody FavoriteFolderUpdateDTO request,
                               HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        favoriteFolderService.updateFolder(userId, AuthContext.parseFolderId(folderId), request);
        return Result.success();
    }

    @DeleteMapping("/me/favorite-folders/{folderId}")
    public Result<Void> delete(@PathVariable("folderId") String folderId,
                               HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        favoriteFolderService.deleteFolder(userId, AuthContext.parseFolderId(folderId));
        return Result.success();
    }

    @PutMapping("/me/favorite-folders/reorder")
    public Result<Void> reorder(@RequestBody FavoriteFolderReorderDTO request,
                                HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        favoriteFolderService.reorderFolders(userId, request);
        return Result.success();
    }
}
