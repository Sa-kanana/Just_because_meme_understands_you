package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量或单条移动收藏到目标夹。
 */
@Data
public class FavoriteMoveDTO {

    /** 目标夹 id，0=默认夹 */
    private Long targetFolderId;

    /** 待移动的梗 id 列表 */
    private List<Long> memeIds;
}
