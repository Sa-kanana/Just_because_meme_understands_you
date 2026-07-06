package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量移动收藏结果。
 */
@Data
public class FavoriteMoveResultVO {

    private Integer movedCount;

    private List<Long> failed = new ArrayList<>();
}
