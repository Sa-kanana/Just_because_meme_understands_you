package com.sakana.just_because_meme_understands_you.modules.meme.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 *
 * </p>
 *
 * @author sakana
 * @since 2026-03-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("meme_tag")
public class MemeTag implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 唯一标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 标签名
     */
    @TableField("name")
    private String name;

    /** 中文注释：标签所属分类代码，用于管理筛选和用户端分组展示。 */
    @TableField("category")
    private String category;

    /** 中文注释：标签在分类内的展示顺序，数值越小越靠前。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 中文注释：查询投影字段，表示该标签是否是当前梗的主标签，不映射到 meme_tag 表列。 */
    @TableField(exist = false)
    private Boolean isPrimary;

    /**
     * 相关梗的数量
     */
    @TableField("related_quantity")
    private Integer relatedQuantity;


}
