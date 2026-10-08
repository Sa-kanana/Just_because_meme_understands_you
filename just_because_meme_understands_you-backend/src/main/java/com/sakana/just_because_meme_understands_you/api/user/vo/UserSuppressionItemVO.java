package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSuppressionItemVO {
    private Long targetUserId;
    private String type;
    private String direction;
    private String nickname;
    private String avatar;
    private LocalDateTime createdAt;
}
