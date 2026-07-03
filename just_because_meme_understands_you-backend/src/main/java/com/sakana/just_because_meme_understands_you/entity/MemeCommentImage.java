package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("meme_comment_images")
public class MemeCommentImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("meme_comment_id")
    private Long memeCommentId;

    private String url;

    @TableField("sort_order")
    private Integer sortOrder;
}
