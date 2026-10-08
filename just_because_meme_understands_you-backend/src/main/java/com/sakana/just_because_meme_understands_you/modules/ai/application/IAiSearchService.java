package com.sakana.just_because_meme_understands_you.modules.ai.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.AiSearchStreamRequestDTO;

public interface IAiSearchService {

    /**
     * 同步准备并验证搜索请求，返回可独立订阅的惰性事件流。
     */
    AiSearchPreparation prepareSearch(Long userId, AiSearchStreamRequestDTO request);
}
