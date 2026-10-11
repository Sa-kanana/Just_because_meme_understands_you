package com.sakana.just_because_meme_understands_you.modules.admin.application.support;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** 管理员知识文件上传的有界资源配置。 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "knowledge.upload")
public class KnowledgeUploadProperties {

    /** 单个知识文件允许上传的最大体积，范围 1 B–25 MiB。 */
    private DataSize maxFileSize = DataSize.ofMegabytes(25);

    /** 单份文档提取正文的最大字符数，超过后拒绝入库而不截断。 */
    @Min(1)
    @Max(1_000_000)
    private int maxContentChars = 1_000_000;

    /** 同一实例同时允许进入 multipart 解析的请求数，限制进程内上传文件暂存量。 */
    @Min(1)
    @Max(4)
    private int maxConcurrentRequests = 2;

    /** 等待解析并发槽位的最长时间，超时后返回繁忙响应。 */
    @Min(1_000)
    @Max(60_000)
    private long admissionWaitMs = 30_000L;

    /** 中文注释：确保 multipart 内存上限与该配置相容，并限制单次请求的峰值内存。 */
    @AssertTrue(message = "knowledge.upload.max-file-size 必须大于 0 且不超过 25 MiB")
    public boolean isMaxFileSizeWithinAllowedRange() {
        return maxFileSize != null
                && maxFileSize.toBytes() > 0
                && maxFileSize.toBytes() <= 25L * 1024 * 1024;
    }
}
