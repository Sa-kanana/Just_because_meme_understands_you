package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

import java.util.List;

@Data
public class FavoriteFolderListVO {

    private List<FavoriteFolderVO> folders;

    private Integer total;
}
