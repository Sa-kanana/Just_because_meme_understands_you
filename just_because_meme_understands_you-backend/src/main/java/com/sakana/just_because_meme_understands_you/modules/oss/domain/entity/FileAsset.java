package com.sakana.just_because_meme_understands_you.modules.oss.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 中文注释：OSS 对象的所有权、元数据和生命周期状态。 */
@Data
@TableName("file_asset")
public class FileAsset {

    /** 中文注释：文件资产主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 中文注释：OSS 对象键，不包含访问域名。 */
    @TableField("object_key")
    private String objectKey;

    /** 中文注释：上传者用户ID，历史回填资产可为空。 */
    @TableField("owner_id")
    private Long ownerId;

    /** 中文注释：上传业务场景，例如 avatar、meme 或 comment。 */
    @TableField("scene")
    private String scene;

    /** 中文注释：客户端声明的原始文件名，仅用于展示和审计。 */
    @TableField("original_filename")
    private String originalFilename;

    /** 中文注释：文件 MIME 类型，历史资产未知时为空。 */
    @TableField("content_type")
    private String contentType;

    /** 中文注释：文件字节数，历史资产未知时为空。 */
    @TableField("file_size")
    private Long fileSize;

    /** 中文注释：资产生命周期状态，值来自 FileAssetStatus。 */
    @TableField("status")
    private String status;

    /** 中文注释：服务端确认 OSS 上传成功的时间。 */
    @TableField("uploaded_at")
    private LocalDateTime uploadedAt;

    /** 中文注释：资产首次绑定业务引用的时间。 */
    @TableField("activated_at")
    private LocalDateTime activatedAt;

    /** 中文注释：允许执行 OSS 删除的最早时间。 */
    @TableField("delete_after")
    private LocalDateTime deleteAfter;

    /** 中文注释：OSS 对象确认删除的时间。 */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /** 中文注释：资产记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 中文注释：资产记录最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
