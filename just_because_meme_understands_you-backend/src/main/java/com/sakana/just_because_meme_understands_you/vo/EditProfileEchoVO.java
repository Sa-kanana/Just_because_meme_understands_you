package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

@Data
public class EditProfileEchoVO {

    private String avatar;

    private String nickname;

    private String signature;

    private Integer gender;

    private String birthday;
}
