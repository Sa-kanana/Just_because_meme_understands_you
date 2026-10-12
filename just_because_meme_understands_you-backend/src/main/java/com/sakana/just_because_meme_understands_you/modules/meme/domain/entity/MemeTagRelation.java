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
@TableName("meme_tag_relation")
public class MemeTagRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 梗ID
     */
    @TableId(value = "meme_id", type = IdType.AUTO)
    private Long memeId;

    /**
     * 标签ID
     */
    @TableField("meme_tag_id")
    private Integer memeTagId;

    /** 中文注释：是否为该梗的主分类标签，1为主标签，0为普通标签。 */
    @TableField("is_primary")
    private Integer isPrimary;

    /** 中文注释：主标签唯一约束辅助列，主标签行写入梗ID，普通标签行保持空值。 */
    @TableField("primary_meme_id")
    private Long primaryMemeId;


}
