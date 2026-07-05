package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 用户发布列表中的标签项
 *
 * @author sakana
 */
@Data
public class UserMemeTagVO {

    /** 标签 id */
    private Integer tagId;

    /** 标签名 */
    private String name;
}
