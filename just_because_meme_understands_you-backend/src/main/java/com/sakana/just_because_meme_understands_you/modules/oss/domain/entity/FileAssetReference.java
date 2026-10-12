package com.sakana.just_because_meme_understands_you.modules.oss.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 中文注释：文件资产当前被哪个业务字段引用的记录。 */
@Data
@TableName("file_asset_ref")
public class FileAssetReference {

    /** 中文注释：文件资产引用主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 中文注释：被业务引用的文件资产ID。 */
    @TableField("asset_id")
    private Long assetId;

    /** 中文注释：引用方业务类型，例如 USER、MEME 或 BANNER。 */
    @TableField("biz_type")
    private String bizType;

    /** 中文注释：引用方业务记录ID。 */
    @TableField("biz_id")
    private Long bizId;

    /** 中文注释：业务字段位置，例如 AVATAR、COVER 或资源行ID。 */
    @TableField("field_key")
    private String fieldKey;

    /** 中文注释：业务引用创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
