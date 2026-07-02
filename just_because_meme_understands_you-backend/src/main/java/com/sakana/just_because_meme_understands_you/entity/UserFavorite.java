package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_favorite")
public class UserFavorite {

    @TableId
    private Long id;

    private Long userId;

    private Long memeId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
