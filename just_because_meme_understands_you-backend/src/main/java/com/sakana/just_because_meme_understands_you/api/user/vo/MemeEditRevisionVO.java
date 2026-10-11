package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 中文注释：返回作者梗修改稿的审核状态和结果说明。 */
@Data
public class MemeEditRevisionVO {

    /** 中文注释：修改稿记录ID。 */
    private Long revisionId;

    /** 中文注释：被修改的梗ID。 */
    private Long memeId;

    /** 中文注释：修改稿审核状态。 */
    private String status;

    /** 中文注释：修改稿提交时间。 */
    private LocalDateTime createdAt;

    /** 中文注释：管理员驳回或取消说明。 */
    private String reviewNote;
}
