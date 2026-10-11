package com.sakana.just_because_meme_understands_you.modules.ai.application.support;

import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 业务上下文组装器。
 * 从业务 MySQL 组装 Agent 业务上下文（禁止 Python 直连 MySQL）。
 */
@Component
public class AiBusinessContextAssembler {

    private static final int DEFAULT_HINT_LIMIT = 8;
    private static final int INTRO_SNIPPET_MAX = 180;

    private final MemeCatalogPort memeCatalogPort;

    public AiBusinessContextAssembler(MemeCatalogPort memeCatalogPort) {
        this.memeCatalogPort = memeCatalogPort;
    }

    public AssembledContext assemble(String query) {
        String keyword = query != null ? query.trim() : "";
        if (!StringUtils.hasText(keyword)) {
            return AssembledContext.empty();// 空查询直接返回空上下文
        }
        // 过长问句截断后再做 LIKE，避免无效匹配
        List<MemeCatalogPort.MemeCard> records =
                memeCatalogPort.searchPublishedByKeyword(keyword, DEFAULT_HINT_LIMIT);
        if (records.isEmpty()) {
            return AssembledContext.empty();
        }

        List<Long> memeIds = records.stream().map(MemeCatalogPort.MemeCard::id).toList();
        Map<Long, List<MemeCatalogPort.MemeTagSummary>> tagsByMeme = memeCatalogPort.findTagsByMemeIds(memeIds);

        List<String> hintIds = new ArrayList<>();
        List<Map<String, Object>> snippets = new ArrayList<>();
        for (MemeCatalogPort.MemeCard meme : records) {
            if (meme == null || meme.id() == null) {
                continue;
            }
            String id = String.valueOf(meme.id());
            hintIds.add(id);
            Map<String, Object> snippet = new LinkedHashMap<>();
            snippet.put("meme_id", id);
            snippet.put("title", meme.name() == null ? "" : meme.name());
            snippet.put("introduction", truncate(meme.introduction(), INTRO_SNIPPET_MAX));
            snippet.put("tags", tagsByMeme.getOrDefault(meme.id(), List.of()).stream()
                    .map(MemeCatalogPort.MemeTagSummary::name).toList());
            snippets.add(snippet);
        }

        Map<String, Object> extra = new HashMap<>();
        extra.put("snippets", snippets);
        extra.put("source", "mysql_keyword");
        return new AssembledContext(hintIds, extra);
    }

    private static String truncate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }

    public record AssembledContext(List<String> hintMemeIds, Map<String, Object> extra) {
        public static AssembledContext empty() {
            return new AssembledContext(List.of(), Map.of());
        }
    }
}
