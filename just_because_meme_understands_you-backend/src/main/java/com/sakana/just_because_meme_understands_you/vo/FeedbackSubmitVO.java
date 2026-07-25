package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 提交反馈结果。
 */
@Data
public class FeedbackSubmitVO {

    /** 是否已受理（邮件已发出） */
    private Boolean accepted;

    /** 提示文案 */
    private String message;
}
