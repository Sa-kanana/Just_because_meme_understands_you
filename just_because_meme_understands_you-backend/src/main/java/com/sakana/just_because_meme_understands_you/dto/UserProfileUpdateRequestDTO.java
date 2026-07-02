package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

@Data
public class UserProfileUpdateRequestDTO {

    private String nickname;

    private Integer gender;

    private String birthday;

    private String signature;

    private String avatar;
}
