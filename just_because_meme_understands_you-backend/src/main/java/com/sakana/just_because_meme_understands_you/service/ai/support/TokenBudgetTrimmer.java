package com.sakana.just_because_meme_understands_you.service.ai.support;

import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.entity.AiChatMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 会话历史 Token 预算裁剪（Java SSOT）。
 */
@Component
public class TokenBudgetTrimmer {

    private final MemeAgentProperties properties;

    public TokenBudgetTrimmer(MemeAgentProperties properties) {
        this.properties = properties;
    }

    public List<AiChatMessage> trim(List<AiChatMessage> messages, int maxTokens) {
        //空列表直接返回
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        //确定 Token 预算（默认 3000）
        int budget = maxTokens > 0 ? maxTokens : properties.getAi().getMaxHistoryTokens();
        //按创建时间升序排序
        List<AiChatMessage> sorted = messages.stream()
                .sorted(Comparator.comparing(AiChatMessage::getCreateTime))
                .toList();

        //从最新消息开始倒序遍历，保留最近的对话
        List<AiChatMessage> kept = new ArrayList<>();
        int used = 0;
        for (int i = sorted.size() - 1; i >= 0; i--) {
            AiChatMessage msg = sorted.get(i);
            int estimate = estimateTokens(msg.getContent());//估算 Token 数
            if (used + estimate > budget && !kept.isEmpty()) {
                break;// 超出预算且已保留至少一条消息，停止
            }
            kept.add(0, msg);// 插入到列表头部，保持时间顺序
            used += estimate;
        }
        return kept;
    }

    public int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        //每 3 个字符约等于 1 个 Token
        int charsPerToken = Math.max(1, properties.getAi().getCharsPerTokenEstimate());
        return Math.max(1, (text.length() + charsPerToken - 1) / charsPerToken);
    }
}
