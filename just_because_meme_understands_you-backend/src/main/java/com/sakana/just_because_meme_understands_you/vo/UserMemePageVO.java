package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 用户发布梗分页响应
 *
 * @author sakana
 */
@Data
public class UserMemePageVO {

    /** 当前页列表 */
    private List<UserPublishedMemeVO> list;

    /** 总数 */
    private long total;

    /** 是否是本人查看（决定是否展示全部状态） */
    @JsonProperty("isOwner")
    private boolean isOwner;
}
