package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.Map;

@Data
public class NotificationJumpVO {

    /** 前端路由 name，如 memeDetail / userProfile */
    private String name;

    /** 路由 params */
    private Map<String, String> params;

    /** 路由 query */
    private Map<String, String> query;
}
