package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_stats")
public class UserStats {

    @TableId("user_id")
    private Long userId;

    private Integer memeCount;

    private Integer likeReceived;

    private Integer favoriteCount;

    private Integer followCount;

    private Integer fansCount;
}
