package com.sakana.just_because_meme_understands_you.modules.meme.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 中文注释：作者提交的梗内容修改快照及其审核记录。 */
@Data
@TableName("meme_edit_revision")
public class MemeEditRevision {

    /** 中文注释：修改稿记录ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 中文注释：被修改的正式梗ID。 */
    @TableField("meme_id")
    private Long memeId;

    /** 中文注释：提交修改的作者用户ID。 */
    @TableField("editor_user_id")
    private Long editorUserId;

    /** 中文注释：提交时正式内容的SHA-256摘要。 */
    @TableField("base_content_hash")
    private String baseContentHash;

    /** 中文注释：包含正文、标签和资源的完整修改内容JSON。 */
    @TableField("content_json")
    private String contentJson;

    /** 中文注释：审核状态，取值PENDING、APPROVED、REJECTED或CANCELED。 */
    @TableField("status")
    private String status;

    /** 中文注释：管理员驳回或取消修改稿的说明。 */
    @TableField("review_note")
    private String reviewNote;

    /** 中文注释：处理该修改稿的管理员用户ID。 */
    @TableField("reviewed_by")
    private Long reviewedBy;

    /** 中文注释：修改稿提交时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 中文注释：修改稿审核完成时间。 */
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;
}
