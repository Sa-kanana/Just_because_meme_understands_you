package com.sakana.just_because_meme_understands_you.api.admin.vo;

import lombok.Data;

@Data
public class AdminMemeActionVO {

    private Long memeId;
    private Integer status;
    private String statusDesc;
}
