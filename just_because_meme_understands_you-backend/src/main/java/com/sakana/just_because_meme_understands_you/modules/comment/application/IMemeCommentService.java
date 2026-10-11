package com.sakana.just_because_meme_understands_you.modules.comment.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.MemeCommentCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentAnchorVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentCreateResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentPageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeReplyCommentVO;

import java.util.List;

public interface IMemeCommentService {

    MemeCommentPageVO pageRootComments(Long memeId, Integer page, Integer size, String sortType, Long currentUserId);

    List<MemeReplyCommentVO> pageReplies(Long rootId, Integer page, Integer size, Long currentUserId);

    MemeCommentCreateResponseVO createComment(Long userId, MemeCommentCreateRequestDTO request);

    /** 删除当前用户自己的评论。 */
    void deleteComment(Long userId, Long commentId);

    /**
     * 按评论 id 定位所属梗与根评论，供消息中心跳转。
     */
    MemeCommentAnchorVO locateComment(Long commentId, Long currentUserId);
}
