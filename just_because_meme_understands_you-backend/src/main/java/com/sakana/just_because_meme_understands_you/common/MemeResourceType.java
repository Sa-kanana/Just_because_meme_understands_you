package com.sakana.just_because_meme_understands_you.common;

import org.springframework.util.StringUtils;

/**
 * meme_resource.resource_type 与接口字段 type 的映射。
 */
public enum MemeResourceType {

    LINK(1, "link"),
    MEDIA(2, "media"),
    VIDEO(3, "video"),
    ARTICLE(4, "article"),
    IMAGE(5, "image");

    private final int code;
    private final String apiType;

    MemeResourceType(int code, String apiType) {
        this.code = code;
        this.apiType = apiType;
    }

    public int getCode() {
        return code;
    }

    public String getApiType() {
        return apiType;
    }

    public static MemeResourceType fromApiType(String type) {
        if (!StringUtils.hasText(type)) {
            return LINK;
        }
        String normalized = type.trim().toLowerCase();
        for (MemeResourceType value : values()) {
            if (value.apiType.equals(normalized)) {
                return value;
            }
        }
        return LINK;
    }

    public static MemeResourceType fromCode(Integer code) {
        if (code == null) {
            return LINK;
        }
        for (MemeResourceType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return LINK;
    }
}
