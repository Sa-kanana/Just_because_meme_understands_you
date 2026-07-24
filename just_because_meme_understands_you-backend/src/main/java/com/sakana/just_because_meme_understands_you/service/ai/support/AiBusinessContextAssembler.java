package com.sakana.just_because_meme_understands_you.service.ai.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.dto.MemeTagBindDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagRelationMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从业务 MySQL 组装 Agent 业务上下文（禁止 Python 直连 MySQL）。
 */
@Component
public class AiBusinessContextAssembler {

    private static final int DEFAULT_HINT_LIMIT = 8;
    private static final int INTRO_SNIPPET_MAX = 180;

    private final MemeMapper memeMapper;
    private final MemeTagRelationMapper memeTagRelationMapper;

    public AiBusinessContextAssembler(MemeMapper memeMapper,
                                      MemeTagRelationMapper memeTagRelationMapper) {
        this.memeMapper = memeMapper;
        this.memeTagRelationMapper = memeTagRelationMapper;
    }

    public AssembledContext assemble(String query) {
        String keyword = query != null ? query.trim() : "";
        if (!StringUtils.hasText(keyword)) {
            return AssembledContext.empty();
        }
        // 过长问句截断后再做 LIKE，避免无效匹配
        String searchKey = keyword.length() > 64 ? keyword.substring(0, 64) : keyword;

        IPage<Meme> page = memeMapper.searchByKeyword(
                new Page<>(1, DEFAULT_HINT_LIMIT, false),
                searchKey,
                "1",
                null,
                null);
        List<Meme> records = page != null && page.getRecords() != null ? page.getRecords() : List.of();
        if (records.isEmpty()) {
            return AssembledContext.empty();
        }

        List<Integer> memeIds = new ArrayList<>();
        for (Meme meme : records) {
            if (meme != null && meme.getId() != null) {
                memeIds.add(meme.getId());
            }
        }
        Map<Integer, List<String>> tagMap = loadTagNames(memeIds);

        List<String> hintIds = new ArrayList<>();
        List<Map<String, Object>> snippets = new ArrayList<>();
        for (Meme meme : records) {
            if (meme == null || meme.getId() == null) {
                continue;
            }
            String id = String.valueOf(meme.getId());
            hintIds.add(id);
            Map<String, Object> snippet = new LinkedHashMap<>();
            snippet.put("meme_id", id);
            snippet.put("title", meme.getName() == null ? "" : meme.getName());
            snippet.put("introduction", truncate(meme.getIntroduction(), INTRO_SNIPPET_MAX));
            snippet.put("tags", tagMap.getOrDefault(meme.getId(), List.of()));
            snippets.add(snippet);
        }

        Map<String, Object> extra = new HashMap<>();
        extra.put("snippets", snippets);
        extra.put("source", "mysql_keyword");
        return new AssembledContext(hintIds, extra);
    }

    private Map<Integer, List<String>> loadTagNames(List<Integer> memeIds) {
        if (memeIds.isEmpty()) {
            return Map.of();
        }
        List<MemeTagBindDTO> rows = memeTagRelationMapper.selectTagsByMemeIds(memeIds);
        Map<Integer, List<String>> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (MemeTagBindDTO row : rows) {
            if (row == null || row.getMemeId() == null || !StringUtils.hasText(row.getName())) {
                continue;
            }
            map.computeIfAbsent(row.getMemeId(), k -> new ArrayList<>()).add(row.getName().trim());
        }
        return map;
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
