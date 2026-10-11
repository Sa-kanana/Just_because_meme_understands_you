package com.sakana.just_because_meme_understands_you.modules.oss.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 中文注释：OSS 对账过程中发现且需要延迟复核的未登记对象。 */
@Data
@TableName("file_orphan_candidate")
public class FileOrphanCandidate {

    /** 中文注释：OSS 孤儿候选主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 中文注释：OSS 中发现但资产表未登记的对象键。 */
    @TableField("object_key")
    private String objectKey;

    /** 中文注释：候选复核状态。 */
    @TableField("status")
    private String status;

    /** 中文注释：首次发现该对象的时间。 */
    @TableField("first_seen_at")
    private LocalDateTime firstSeenAt;

    /** 中文注释：最近一次仍发现该对象的时间。 */
    @TableField("last_seen_at")
    private LocalDateTime lastSeenAt;

    /** 中文注释：满足最短复查间隔后的确认时间。 */
    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;

    /** 中文注释：最近一次对账检查结果摘要。 */
    @TableField("last_check_result")
    private String lastCheckResult;

    /** 中文注释：候选记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 中文注释：候选记录最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
