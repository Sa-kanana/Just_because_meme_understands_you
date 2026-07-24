package com.sakana.just_because_meme_understands_you.service.ai;

import com.sakana.just_because_meme_understands_you.vo.AiChatMessagePageVO;
import com.sakana.just_because_meme_understands_you.vo.AiChatSessionPageVO;

public interface IAiChatSessionService {

    AiChatSessionPageVO pageSessions(Long userId, Integer page, Integer size);

    AiChatMessagePageVO pageMessages(Long userId, Long sessionId, Integer page, Integer size);

    void softDeleteSession(Long userId, Long sessionId);
}
