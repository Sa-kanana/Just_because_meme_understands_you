package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 梗作者信息，对齐 Apifox Author schema。
 */
@Data
public class AuthorVO {

    /** 用户 ID（字符串，避免前端精度丢失） */
    private String userId;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 个性签名 */
    private String signature;
}
