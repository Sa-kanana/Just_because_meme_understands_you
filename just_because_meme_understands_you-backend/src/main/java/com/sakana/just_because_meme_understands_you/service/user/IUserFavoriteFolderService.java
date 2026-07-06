package com.sakana.just_because_meme_understands_you.service.user;

import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderCreateDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderReorderDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteFolderUpdateDTO;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderListVO;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderVO;

/**
 * 收藏夹服务：CRUD、排序、删除归档、计数与缓存维护。
 */
public interface IUserFavoriteFolderService {

    /** 虚拟默认夹 id */
    long DEFAULT_FOLDER_ID = 0L;

    /** 每用户最大自定义夹数 */
    int MAX_FOLDER_COUNT = 50;

    /** 夹名称最大长度 */
    int MAX_NAME_LENGTH = 64;

    /** 简介最大长度 */
    int MAX_DESC_LENGTH = 255;

    /**
     * 当前用户收藏夹列表（含默认夹）。
     */
    FavoriteFolderListVO listMyFolders(Long userId);

    /**
     * 查看 targetUser 的收藏夹：本人返回全部含默认夹；他人仅返回公开自定义夹。
     */
    FavoriteFolderListVO listFolders(Long targetUserId, Long currentUserId);

    FavoriteFolderVO createFolder(Long userId, FavoriteFolderCreateDTO request);

    void updateFolder(Long userId, Long folderId, FavoriteFolderUpdateDTO request);

    /**
     * 删除收藏夹：夹内收藏移入默认夹，meme_count 归零。
     */
    void deleteFolder(Long userId, Long folderId);

    void reorderFolders(Long userId, FavoriteFolderReorderDTO request);

    /**
     * 校验 folderId 归属当前用户且未删除；默认夹直接放行。
     */
    void assertFolderOwnedByUser(Long userId, long folderId);

    /**
     * 调整夹冗余计数：delta 正负。默认夹不维护 DB 行，跳过。
     */
    void adjustMemeCount(long folderId, int delta);

    /**
     * 失效用户收藏夹列表缓存。
     */
    void evictFolderCache(Long userId);

    /**
     * 默认收藏夹是否对他人公开（未自定义时视为公开）。
     */
    boolean isDefaultFolderPublic(Long userId);
}
