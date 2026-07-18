package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 评论定位：供消息跳转展开根评论并滚动到目标评论。
 */
@Data
public class MemeCommentAnchorVO {

    private Long memeId;

    private Long commentId;

    /** 根评论 id；若目标本身是根评论，则等于 commentId */
    private Long rootId;

    private Long parentId;

    private Boolean root;
}
