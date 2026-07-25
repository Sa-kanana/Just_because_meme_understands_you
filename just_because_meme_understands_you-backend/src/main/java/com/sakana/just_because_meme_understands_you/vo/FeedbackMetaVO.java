package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.List;

/**
 * 反馈页元数据（类型选项等）。
 */
@Data
public class FeedbackMetaVO {

    private List<FeedbackCategoryOptionVO> categories;

    private String notice;
}
