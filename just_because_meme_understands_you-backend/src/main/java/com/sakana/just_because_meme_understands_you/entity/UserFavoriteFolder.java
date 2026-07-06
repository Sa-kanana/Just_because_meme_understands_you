package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户自定义收藏夹。id=0 为虚拟默认夹，不占用本表行。
 */
@Data
@TableName("user_favorite_folder")
public class UserFavoriteFolder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String name;

    private String description;

    @TableField("is_public")
    private Integer isPublic;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("meme_count")
    private Integer memeCount;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
