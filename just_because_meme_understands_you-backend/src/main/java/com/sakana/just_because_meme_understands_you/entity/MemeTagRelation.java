package com.sakana.just_because_meme_understands_you.entity;

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
    private Integer memeId;

    /**
     * 标签ID
     */
    @TableField("meme_tag_id")
    private Integer memeTagId;


}
