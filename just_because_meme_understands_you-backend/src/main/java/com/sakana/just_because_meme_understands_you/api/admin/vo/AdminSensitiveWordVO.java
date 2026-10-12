package com.sakana.just_because_meme_understands_you.api.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminSensitiveWordVO {

    private Long id;
    private String word;
    private String category;
    /** 中文注释：风险等级，取值 NORMAL、REVIEW 或 BLOCK。 */
    private String riskLevel;
    /** 中文注释：NORMAL 等级命中后的替换文本。 */
    private String replacement;
    private Integer status;
    private String creator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
