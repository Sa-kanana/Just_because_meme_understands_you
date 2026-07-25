package com.sakana.just_because_meme_understands_you.service.feedback;

import com.sakana.just_because_meme_understands_you.dto.FeedbackSubmitRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.FeedbackMetaVO;
import com.sakana.just_because_meme_understands_you.vo.FeedbackSubmitVO;

/**
 * 用户反馈门面。
 */
public interface IFeedbackService {

    FeedbackMetaVO getMeta();

    FeedbackSubmitVO submit(Long userId, FeedbackSubmitRequestDTO request, String clientIp);
}
