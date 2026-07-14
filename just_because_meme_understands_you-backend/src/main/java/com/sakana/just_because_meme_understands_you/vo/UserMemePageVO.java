package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 用户发布梗分页响应（Apifox：Author 字段 + list/page/size/hasMore）
 *
 * @author sakana
 */
@Data
public class UserMemePageVO {

    /** 目标用户作者信息 */
    private AuthorVO author;

    /** 当前页列表 */
    private List<UserPublishedMemeVO> list;

    /** 当前页码 */
    private Integer page;

    /** 每页条数 */
    private Integer size;

    /** 总数（兼容旧前端） */
    private long total;

    /** 是否是本人查看（决定是否展示全部状态） */
    @JsonProperty("isOwner")
    private boolean isOwner;

    /** 是否还有下一页 */
    private Boolean hasMore;
}
