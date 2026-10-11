package com.sakana.just_because_meme_understands_you.modules.ai.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.AiChatMessageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AiChatSessionVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AiChatMessage;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AiChatSession;
import org.mapstruct.Mapper;

/**
 * AI 会话和消息视图的字段映射器。
 */
@Mapper(config = MapStructConfig.class)
public interface AiChatViewMapper {

    /**
     * 将会话实体转换为用户端视图。
     *
     * @param source 会话实体
     * @return 会话视图
     */
    AiChatSessionVO toSessionVO(AiChatSession source);

    /**
     * 将消息实体转换为用户端视图。
     *
     * @param source 消息实体
     * @return 消息视图
     */
    AiChatMessageVO toMessageVO(AiChatMessage source);
}
