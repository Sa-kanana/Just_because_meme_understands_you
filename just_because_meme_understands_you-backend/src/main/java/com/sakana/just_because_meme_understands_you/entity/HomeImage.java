package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首页轮播图
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("home_image")
public class HomeImage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 轮播图标题（可选，用于悬停显示） */
    @TableField("title")
    private String title;

    /** 图片存放地址（OSS链接或本地路径） */
    @TableField("img_url")
    private String imgUrl;

    /** 跳转类型：0-无跳转，1-内部文章/梗ID，2-外部链接 */
    @TableField("target_type")
    private Integer targetType;

    /** 跳转目标值（如文章ID或URL） */
    @TableField("target_value")
    private String targetValue;

    /** 排序权重（数值越大越靠前） */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 状态：0-下线，1-上线 */
    @TableField("status")
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
