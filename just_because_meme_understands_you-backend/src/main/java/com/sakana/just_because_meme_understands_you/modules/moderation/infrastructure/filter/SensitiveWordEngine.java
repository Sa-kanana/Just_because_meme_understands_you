package com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.filter;

import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveLevel;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveWordRule;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** 中文注释：使用不可变 DFA 词库快照执行并发安全的敏感词匹配。 */
public class SensitiveWordEngine {

    /** 中文注释：当前生效的完整快照，重建完成后一次性原子替换。 */
    private final AtomicReference<DictionarySnapshot> snapshotRef = new AtomicReference<>(
            new DictionarySnapshot(0L, createWordTree(), Map.of()));

    /**
     * 中文注释：根据已启用规则构建全新词树，构建完毕后原子替换，不暴露半成品。
     *
     * @param rules 已启用的敏感词规则
     * @param version 数据库中的词库版本
     */
    public void rebuild(List<SensitiveWordRule> rules, long version) {
        Map<String, SensitiveWordRule> normalizedRules = new LinkedHashMap<>();
        if (rules != null) {
            for (SensitiveWordRule rule : rules) {
                if (rule == null || rule.word() == null || rule.word().isBlank() || rule.level() == null) {
                    continue;
                }
                String normalizedWord = normalize(rule.word().trim()).text();
                if (normalizedWord.isEmpty()) {
                    continue;
                }
                normalizedRules.merge(normalizedWord, rule,
                        SensitiveWordEngine::preferHigherRiskRule);
            }
        }

        WordTree nextWordTree = createWordTree();
        normalizedRules.keySet().forEach(nextWordTree::addWord);
        snapshotRef.set(new DictionarySnapshot(version, nextWordTree, Map.copyOf(normalizedRules)));
    }

    /** 中文注释：返回当前已加载的词库版本，供 Redis 通知和定时同步比较。 */
    public long version() {
        return snapshotRef.get().version();
    }

    /**
     * 中文注释：标准化输入后执行最长优先匹配，结果索引映射回原始文本。
     *
     * @param rawContent 待检测原文
     * @return 分级处置结果及原文命中位置
     */
    public SensitiveFilterResult filter(String rawContent) {
        DictionarySnapshot snapshot = snapshotRef.get();
        if (rawContent == null || rawContent.isBlank() || snapshot.rules().isEmpty()) {
            return SensitiveFilterResult.pass(rawContent);
        }

        NormalizedText normalizedText = normalize(rawContent);
        if (normalizedText.text().isEmpty()) {
            return SensitiveFilterResult.pass(rawContent);
        }
        List<SensitiveWordMatch> matches = findMatches(snapshot, normalizedText);
        if (matches.isEmpty()) {
            return SensitiveFilterResult.pass(rawContent);
        }

        SensitiveFilterResult.Decision decision = matches.stream().anyMatch(
                match -> match.level() == SensitiveLevel.BLOCK)
                ? SensitiveFilterResult.Decision.BLOCK
                : matches.stream().anyMatch(match -> match.level() == SensitiveLevel.REVIEW)
                ? SensitiveFilterResult.Decision.REVIEW
                : SensitiveFilterResult.Decision.PASS;
        if (decision != SensitiveFilterResult.Decision.PASS) {
            return SensitiveFilterResult.of(decision, rawContent, matches);
        }

        StringBuilder filteredContent = new StringBuilder(rawContent);
        List<Replacement> replacements = selectReplacements(matches, snapshot.rules());
        for (int index = replacements.size() - 1; index >= 0; index--) {
            Replacement replacement = replacements.get(index);
            filteredContent.replace(replacement.start(), replacement.endExclusive(), replacement.value());
        }
        return SensitiveFilterResult.of(decision, filteredContent.toString(), matches);
    }

    private List<SensitiveWordMatch> findMatches(DictionarySnapshot snapshot, NormalizedText content) {
        List<FoundWord> foundWords = snapshot.wordTree().matchAllWords(content.text(), -1, true, true);
        if (foundWords == null || foundWords.isEmpty()) {
            return List.of();
        }

        List<SensitiveWordMatch> matches = new ArrayList<>(foundWords.size());
        for (FoundWord foundWord : foundWords) {
            SensitiveWordRule rule = snapshot.rules().get(foundWord.getWord());
            int normalizedStart = foundWord.getStartIndex();
            int normalizedEnd = foundWord.getEndIndex();
            if (rule == null || normalizedStart < 0 || normalizedEnd < normalizedStart
                    || normalizedEnd >= content.originalStart().size()) {
                continue;
            }
            matches.add(new SensitiveWordMatch(rule.word(), rule.category(), rule.level(),
                    content.originalStart().get(normalizedStart),
                    content.originalEndExclusive().get(normalizedEnd) - 1));
        }
        matches.sort(Comparator.comparingInt(SensitiveWordMatch::start)
                .thenComparing(Comparator.comparingInt((SensitiveWordMatch match) -> match.level().priority())
                        .reversed())
                .thenComparing(Comparator.comparingInt(SensitiveWordMatch::end).reversed()));
        return List.copyOf(matches);
    }

    private List<Replacement> selectReplacements(
            List<SensitiveWordMatch> matches, Map<String, SensitiveWordRule> rules) {
        List<Replacement> replacements = new ArrayList<>();
        int lastEnd = -1;
        for (SensitiveWordMatch match : matches) {
            if (match.start() <= lastEnd || match.level() != SensitiveLevel.NORMAL) {
                continue;
            }
            SensitiveWordRule rule = rules.get(normalize(match.word()).text());
            if (rule == null || rule.replacement() == null || rule.replacement().isEmpty()) {
                continue;
            }
            replacements.add(new Replacement(match.start(), match.end() + 1, rule.replacement()));
            lastEnd = match.end();
        }
        return replacements;
    }

    private static SensitiveWordRule preferHigherRiskRule(SensitiveWordRule left, SensitiveWordRule right) {
        if (right.level().priority() > left.level().priority()) {
            return right;
        }
        if (right.level() == left.level() && right.replacement() != null && !right.replacement().isEmpty()) {
            return right;
        }
        return left;
    }

    /**
     * 中文注释：对每个原始 Unicode 码点执行 NFKC 与小写转换，并删除零宽字符；保留原文索引映射。
     *
     * @param value 原始文本
     * @return 标准化文本及每个 UTF-16 单元对应的原文起止位置
     */
    private static NormalizedText normalize(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        List<Integer> originalStart = new ArrayList<>();
        List<Integer> originalEndExclusive = new ArrayList<>();
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            int sourceEnd = index + Character.charCount(codePoint);
            if (!isZeroWidth(codePoint)) {
                String source = new String(Character.toChars(codePoint));
                String transformed = Normalizer.normalize(source, Normalizer.Form.NFKC)
                        .toLowerCase(Locale.ROOT);
                for (int outputIndex = 0; outputIndex < transformed.length(); outputIndex++) {
                    normalized.append(transformed.charAt(outputIndex));
                    originalStart.add(index);
                    originalEndExclusive.add(sourceEnd);
                }
            }
            index = sourceEnd;
        }
        return new NormalizedText(normalized.toString(), List.copyOf(originalStart),
                List.copyOf(originalEndExclusive));
    }

    private static boolean isZeroWidth(int codePoint) {
        return codePoint == 0x00AD || codePoint == 0x034F || codePoint == 0x061C
                || codePoint == 0x180E || (codePoint >= 0x200B && codePoint <= 0x200F)
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2060 && codePoint <= 0x206F)
                || codePoint == 0xFEFF;
    }

    /** 中文注释：创建仅按字面字符匹配的 DFA，不跨标点吞并匹配。 */
    private static WordTree createWordTree() {
        return new WordTree().setCharFilter(character -> true);
    }

    /** 中文注释：不可变词库快照；WordTree 构造后只读。 */
    private record DictionarySnapshot(long version, WordTree wordTree, Map<String, SensitiveWordRule> rules) {
    }

    /** 中文注释：标准化文本及每个 UTF-16 单元到原始文本的索引映射。 */
    private record NormalizedText(String text, List<Integer> originalStart, List<Integer> originalEndExclusive) {
    }

    /** 中文注释：待应用的原文替换片段。 */
    private record Replacement(int start, int endExclusive, String value) {
    }
}
