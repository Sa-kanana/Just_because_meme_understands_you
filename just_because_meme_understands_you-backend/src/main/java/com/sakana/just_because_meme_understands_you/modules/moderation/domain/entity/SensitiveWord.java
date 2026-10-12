package com.sakana.just_because_meme_understands_you.modules.moderation.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;

    private String category;

    /** 中文注释：风险等级，取值 NORMAL、REVIEW 或 BLOCK。 */
    @TableField("risk_level")
    private String riskLevel;

    /** 中文注释：规则匹配类型；当前支持标准化后的字面匹配 NORMAL。 */
    @TableField("match_type")
    private String matchType;

    /** 中文注释：NORMAL 等级命中后的替换文本；为空表示保留原文。 */
    private String replacement;

    /** 中文注释：该敏感词记录的修订版本。 */
    private Long version;

    private Integer status;

    private String creator;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
