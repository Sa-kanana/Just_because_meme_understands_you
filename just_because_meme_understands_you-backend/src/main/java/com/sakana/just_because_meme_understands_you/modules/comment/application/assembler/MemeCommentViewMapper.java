package com.sakana.just_because_meme_understands_you.modules.comment.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentCreateResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeReplyCommentVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeRootCommentVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 评论实体到用户端视图的纯字段映射器。 */
@Mapper(config = MapStructConfig.class)
public interface MemeCommentViewMapper {

    /** 将评论实体映射为创建结果。 */
    @Mapping(target = "commentId", source = "id")
    MemeCommentCreateResponseVO toCreateResponseVO(MemeComment source);

    /** 映射根评论的基础字段，用户、媒体和统计上下文由应用层填充。 */
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "userAvatar", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "replyCount", ignore = true)
    @Mapping(target = "likes", ignore = true)
    @Mapping(target = "liked", ignore = true)
    @Mapping(target = "owner", ignore = true)
    MemeRootCommentVO toRootVO(MemeComment source);

    /** 映射回复评论的基础字段，回复对象和用户上下文由应用层填充。 */
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "userAvatar", ignore = true)
    @Mapping(target = "replyToUserId", ignore = true)
    @Mapping(target = "replyToUserName", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "likes", ignore = true)
    @Mapping(target = "liked", ignore = true)
    @Mapping(target = "owner", ignore = true)
    MemeReplyCommentVO toReplyVO(MemeComment source);
}
