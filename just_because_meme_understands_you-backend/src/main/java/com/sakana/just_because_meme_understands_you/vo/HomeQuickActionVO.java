package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

@Data
public class HomeQuickActionVO {

    private String key;

    private String label;

    private String route;

    private Boolean requireLogin;
}
