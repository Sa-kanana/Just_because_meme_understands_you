package com.sakana.just_because_meme_understands_you.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 中文注释：客户端申请 OSS 图片上传票据时提交的文件元数据。 */
@Data
public class OssUploadTicketRequestDTO {

    /** 中文注释：上传业务场景，只允许 avatar、meme、comment 或 home。 */
    @NotBlank
    @Size(max = 32)
    private String scene;

    /** 中文注释：上传文件原始名称，服务端只保留文件名和受支持扩展名。 */
    @NotBlank
    @Size(max = 255)
    private String originalFilename;

    /** 中文注释：客户端声明的图片 MIME 类型，服务端会与扩展名交叉校验。 */
    @NotBlank
    @Size(max = 128)
    private String contentType;

    /** 中文注释：客户端声明的文件字节数，服务端限制为不超过10MiB。 */
    @NotNull
    @Positive
    private Long fileSize;
}
