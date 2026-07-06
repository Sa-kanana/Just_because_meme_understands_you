package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

import java.util.List;

@Data
public class FavoriteFolderReorderDTO {

    /** 按数组顺序重写 sort_order，不含默认夹 */
    private List<Long> folderIds;
}
