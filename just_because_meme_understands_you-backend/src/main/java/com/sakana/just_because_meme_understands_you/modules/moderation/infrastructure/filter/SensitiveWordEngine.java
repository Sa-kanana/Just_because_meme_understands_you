package com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.filter;

import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Hutool DFA 关键词树的敏感词过滤器。
 */
public class SensitiveWordEngine {

    /** 人工审核型敏感词的处置类型。 */
    private static final int ACTION_AUDIT = 2;
    /** 拒绝型敏感词的处置类型。 */
    private static final int ACTION_REJECT = 3;

    /** 当前生效的 Hutool 关键词树，重载时整体替换。 */
    private WordTree wordTree = createWordTree();
    /** 当前关键词对应的处置类型，键为去除首尾空白后的敏感词。 */
    private Map<String, Integer> wordActions = Map.of();

    /**
     * 根据数据库中的敏感词及处置类型重建匹配树。
     *
     * @param wordActionMap 敏感词与处置类型的映射
     */
    public synchronized void rebuild(Map<String, Integer> wordActionMap) {
        Map<String, Integer> nextWordActions = new LinkedHashMap<>();
        if (wordActionMap != null) {
            for (Map.Entry<String, Integer> entry : wordActionMap.entrySet()) {
                String word = entry.getKey();
                Integer actionType = entry.getValue();
                if (word == null || word.isBlank() || actionType == null) {
                    continue;
                }
                nextWordActions.merge(word.trim(), actionType, Math::max);
            }
        }

        WordTree nextWordTree = createWordTree();
        nextWordActions.keySet().forEach(nextWordTree::addWord);

        this.wordActions = Map.copyOf(nextWordActions);
        this.wordTree = nextWordTree;
    }

    /**
     * 查找并处理文本中的敏感词；同一位置优先使用最长词，重叠词按从左到右处理。
     *
     * @param rawContent 待过滤的原始文本
     * @return 敏感词处置后的过滤结果
     */
    public synchronized SensitiveFilterResult filter(String rawContent) {
        if (rawContent == null || rawContent.isBlank() || wordActions.isEmpty()) {
            return SensitiveFilterResult.pass(rawContent);
        }

        List<SensitiveWordMatch> matches = findLeftmostLongestMatches(rawContent);
        if (matches.isEmpty()) {
            return SensitiveFilterResult.pass(rawContent);
        }
        if (matches.stream().anyMatch(match -> match.actionType() == ACTION_REJECT)) {
            return SensitiveFilterResult.reject(rawContent);
        }

        StringBuilder filteredContent = new StringBuilder(rawContent);
        boolean needAudit = false;
        for (int index = matches.size() - 1; index >= 0; index--) {
            SensitiveWordMatch match = matches.get(index);
            if (match.actionType() == ACTION_AUDIT) {
                needAudit = true;
            }
            filteredContent.replace(
                    match.startIndex(),
                    match.endIndex() + 1,
                    "*".repeat(match.endIndex() - match.startIndex() + 1)
            );
        }

        if (needAudit) {
            return SensitiveFilterResult.audit(filteredContent.toString());
        }
        return SensitiveFilterResult.replaced(filteredContent.toString());
    }

    /**
     * 收集 Hutool 找到的所有匹配项，并筛选为从左到右的最长非重叠匹配。
     *
     * @param content 待检索文本
     * @return 按起始位置升序排列的有效匹配项
     */
    private List<SensitiveWordMatch> findLeftmostLongestMatches(String content) {
        List<FoundWord> foundWords = wordTree.matchAllWords(content, -1, true, true);
        if (foundWords == null || foundWords.isEmpty()) {
            return List.of();
        }

        List<SensitiveWordMatch> candidates = new ArrayList<>(foundWords.size());
        for (FoundWord foundWord : foundWords) {
            Integer actionType = wordActions.get(foundWord.getWord());
            if (actionType == null) {
                continue;
            }
            candidates.add(new SensitiveWordMatch(
                    foundWord.getStartIndex(),
                    foundWord.getEndIndex(),
                    actionType
            ));
        }

        candidates.sort(Comparator.comparingInt(SensitiveWordMatch::startIndex)
                .thenComparing(Comparator.comparingInt(SensitiveWordMatch::endIndex).reversed()));

        List<SensitiveWordMatch> selectedMatches = new ArrayList<>();
        int lastMatchedEnd = -1;
        for (SensitiveWordMatch candidate : candidates) {
            if (candidate.startIndex() <= lastMatchedEnd) {
                continue;
            }
            selectedMatches.add(candidate);
            lastMatchedEnd = candidate.endIndex();
        }
        return selectedMatches;
    }

    /**
     * 创建按字面字符匹配的 Hutool 关键词树，不忽略文本中的标点或特殊字符。
     *
     * @return 配置完成的关键词树
     */
    private static WordTree createWordTree() {
        return new WordTree().setCharFilter(character -> true);
    }

    /** 敏感词匹配结果，结束位置采用 Hutool 的闭区间索引。 */
    private record SensitiveWordMatch(int startIndex, int endIndex, int actionType) {
    }
}
