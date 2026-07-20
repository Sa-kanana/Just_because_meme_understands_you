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
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int budget = maxTokens > 0 ? maxTokens : properties.getAi().getMaxHistoryTokens();
        List<AiChatMessage> sorted = messages.stream()
                .sorted(Comparator.comparing(AiChatMessage::getCreateTime))
                .toList();

        List<AiChatMessage> kept = new ArrayList<>();
        int used = 0;
        for (int i = sorted.size() - 1; i >= 0; i--) {
            AiChatMessage msg = sorted.get(i);
            int estimate = estimateTokens(msg.getContent());
            if (used + estimate > budget && !kept.isEmpty()) {
                break;
            }
            kept.add(0, msg);
            used += estimate;
        }
        return kept;
    }

    public int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int charsPerToken = Math.max(1, properties.getAi().getCharsPerTokenEstimate());
        return Math.max(1, (text.length() + charsPerToken - 1) / charsPerToken);
    }
}
