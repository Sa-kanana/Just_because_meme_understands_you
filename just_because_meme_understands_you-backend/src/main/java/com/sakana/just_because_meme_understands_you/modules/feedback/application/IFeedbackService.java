package com.sakana.just_because_meme_understands_you.modules.feedback.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.FeedbackSubmitRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.FeedbackMetaVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.FeedbackSubmitVO;

/**
 * 用户反馈门面。
 */
public interface IFeedbackService {

    FeedbackMetaVO getMeta();

    FeedbackSubmitVO submit(Long userId, FeedbackSubmitRequestDTO request, String clientIp);
}
