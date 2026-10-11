package com.sakana.just_because_meme_understands_you.api.admin.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminReportVO {
    private Long id;
    private String targetType;
    private Long targetId;
    private Long memeId;
    private String memeName;
    private Integer memeStatus;
    private Long targetAuthorId;
    private String targetAuthorName;
    private Long commentId;
    private String commentContent;
    private Long commentAuthorId;
    private String commentAuthorName;
    private Long reporterUserId;
    private String reporterName;
    private String reason;
    private String detail;
    private String status;
    private String result;
    private Long handledBy;
    private String handlingNote;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
