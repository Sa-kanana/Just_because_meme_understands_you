package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

@Data
public class UserSuppressionStatusVO {
    private Long targetUserId;
    private boolean muted;
    private boolean blocked;
    private boolean blockedByMe;
    private boolean blockedByTarget;
}
