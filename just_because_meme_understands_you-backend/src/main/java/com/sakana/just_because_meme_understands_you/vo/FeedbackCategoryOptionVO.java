package com.sakana.just_because_meme_understands_you.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 反馈类型选项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackCategoryOptionVO {

    private String value;

    private String label;
}
