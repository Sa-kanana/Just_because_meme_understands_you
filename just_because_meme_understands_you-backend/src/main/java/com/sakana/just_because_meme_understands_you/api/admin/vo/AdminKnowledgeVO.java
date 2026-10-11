package com.sakana.just_because_meme_understands_you.api.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AdminKnowledgeVO {

    private Long id;
    private String title;
    private String content;
    private String category;
    private List<String> tags = new ArrayList<>();
    private String contentHash;
    private Integer status;
    /** 向量处理状态，供管理端区分排队、索引完成和失败。 */
    private String indexStatus;
    /** 当前向量任务版本。 */
    private Long indexRevision;
    /** 当前事务发件箱任务 ID。 */
    private Long indexTaskId;
    /** 最近一次失败摘要；重试期间展示当前错误，终态失败时保留最终错误。 */
    private String indexError;
    /** 正文 SHA-256 指纹重复时标记为 true，表示复用现有文档。 */
    private Boolean duplicate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
