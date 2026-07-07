package com.sakana.just_because_meme_understands_you.service.comment;

import com.sakana.just_because_meme_understands_you.dto.MemeCommentCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentCreateResponseVO;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentPageVO;
import com.sakana.just_because_meme_understands_you.vo.MemeReplyCommentVO;

import java.util.List;

public interface IMemeCommentService {

    MemeCommentPageVO pageRootComments(Long memeId, Integer page, Integer size, String sortType, Long currentUserId);

    List<MemeReplyCommentVO> pageReplies(Long rootId, Integer page, Integer size);

    MemeCommentCreateResponseVO createComment(Long userId, MemeCommentCreateRequestDTO request);
}
