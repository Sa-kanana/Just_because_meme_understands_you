package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.List;

/**
 * 接口 MemeResource：相关链接
 */
@Data
public class MemeResourceVO {

    /**
     * 唯一标识
     */
    private Integer id;

    /**
     * 相关链接
     */
    private List<String> resourceUrl;
}

