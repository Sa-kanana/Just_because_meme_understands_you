package com.sakana.just_because_meme_understands_you.api.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeEditRequestDTO;
import lombok.Data;

import java.time.LocalDateTime;

/** 中文注释：管理端修改稿列表和审核详情视图。 */
@Data
public class AdminMemeEditRevisionVO {

    /** 中文注释：修改稿审核记录ID。 */
    private Long revisionId;

    /** 中文注释：被修改的正式梗ID。 */
    private Long memeId;

    /** 中文注释：提交修改的作者用户ID。 */
    private Long editorUserId;

    /** 中文注释：梗当前名称。 */
    private String memeName;

    /** 中文注释：梗当前生命周期状态。 */
    private Integer memeStatus;

    /** 中文注释：修改稿审核状态。 */
    private String status;

    /** 中文注释：管理员审核说明或驳回原因。 */
    private String reviewNote;

    /** 中文注释：修改稿提交时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 中文注释：修改稿审核完成时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewedAt;

    /** 中文注释：当前正式内容；分页列表可为空，详情接口返回完整内容。 */
    private MemeEditRequestDTO currentContent;

    /** 中文注释：作者提交的修改内容快照。 */
    private MemeEditRequestDTO submittedContent;
}
