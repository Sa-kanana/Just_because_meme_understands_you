package com.sakana.just_because_meme_understands_you.filter;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 轻量布隆过滤器，用于敏感词词典的快速预判。
 */
class SimpleBloomFilter {

    private final BitSet bitSet;
    private final int bitSize;
    private final int hashFunctions;

    SimpleBloomFilter(int expectedInsertions) {
        int size = Math.max(expectedInsertions * 10, 1024);
        this.bitSize = size;
        this.hashFunctions = 3;
        this.bitSet = new BitSet(size);
    }

    void add(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        for (int i = 0; i < hashFunctions; i++) {
            bitSet.set(Math.abs(hash(value, i)) % bitSize);
        }
    }

    boolean mightContain(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < hashFunctions; i++) {
            if (!bitSet.get(Math.abs(hash(value, i)) % bitSize)) {
                return false;
            }
        }
        return true;
    }

    private int hash(String value, int seed) {
        int hash = seed;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash = hash * 31 + b;
        }
        return hash;
    }
}

/**
 * DFA 敏感词匹配树。
 */
class SensitiveWordDfa {

    private final DfaNode root = new DfaNode();

    void addWord(String word, int actionType) {
        if (word == null || word.isBlank()) {
            return;
        }
        DfaNode current = root;
        for (char ch : word.trim().toCharArray()) {
            current = current.children.computeIfAbsent(ch, k -> new DfaNode());
        }
        current.end = true;
        current.word = word.trim();
        current.actionType = Math.max(current.actionType, actionType);
    }

    MatchResult match(String text, int startIndex) {
        DfaNode current = root;
        MatchResult lastMatch = null;
        for (int i = startIndex; i < text.length(); i++) {
            char ch = text.charAt(i);
            current = current.children.get(ch);
            if (current == null) {
                break;
            }
            if (current.end) {
                lastMatch = new MatchResult(i, current.word, current.actionType);
            }
        }
        return lastMatch;
    }

    static class MatchResult {
        final int endIndex;
        final String word;
        final int actionType;

        MatchResult(int endIndex, String word, int actionType) {
            this.endIndex = endIndex;
            this.word = word;
            this.actionType = actionType;
        }
    }

    static class DfaNode {
        final Map<Character, DfaNode> children = new HashMap<>();
        boolean end;
        String word;
        int actionType;
    }
}

/**
 * 布隆过滤器 + DFA 敏感词过滤器。
 */
public class SensitiveWordEngine {

    private static final int ACTION_REPLACE = 1;
    private static final int ACTION_AUDIT = 2;
    private static final int ACTION_REJECT = 3;

    private SimpleBloomFilter bloomFilter = new SimpleBloomFilter(256);
    private SensitiveWordDfa dfa = new SensitiveWordDfa();
    private Set<Character> sensitiveChars = new HashSet<>();

    public synchronized void rebuild(Map<String, Integer> wordActionMap) {
        SimpleBloomFilter nextBloom = new SimpleBloomFilter(Math.max(wordActionMap.size() * 2, 256));
        SensitiveWordDfa nextDfa = new SensitiveWordDfa();
        Set<Character> nextChars = new HashSet<>();
        for (Map.Entry<String, Integer> entry : wordActionMap.entrySet()) {
            String word = entry.getKey();
            Integer actionType = entry.getValue();
            if (word == null || word.isBlank() || actionType == null) {
                continue;
            }
            nextBloom.add(word);
            nextDfa.addWord(word, actionType);
            for (char ch : word.toCharArray()) {
                nextChars.add(ch);
            }
        }
        this.bloomFilter = nextBloom;
        this.dfa = nextDfa;
        this.sensitiveChars = nextChars;
    }

    public synchronized SensitiveFilterResult filter(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return SensitiveFilterResult.pass(rawContent);
        }
        if (sensitiveChars.isEmpty()) {
            return SensitiveFilterResult.pass(rawContent);
        }
        if (!containsSensitiveChar(rawContent)) {
            return SensitiveFilterResult.pass(rawContent);
        }
        if (!bloomMightHit(rawContent)) {
            return SensitiveFilterResult.pass(rawContent);
        }

        StringBuilder builder = new StringBuilder(rawContent);
        boolean replaced = false;
        boolean needAudit = false;
        boolean rejected = false;

        //将文本中的所有敏感词替换为
        for (int i = 0; i < builder.length(); ) {
            SensitiveWordDfa.MatchResult match = dfa.match(builder.toString(), i);
            if (match == null) {
                i++;// 未匹配到，移动指针
                continue;
            }
            if (match.actionType == ACTION_REJECT) {
                rejected = true;
                break;// 拒绝类敏感词，立即中断
            }
            if (match.actionType == ACTION_AUDIT) {
                needAudit = true;// 标记需要人工审核
            }
            // 替换敏感词为 ***
            int start = i;
            int end = match.endIndex;
            builder.replace(start, end + 1, "*".repeat(end - start + 1));
            replaced = true;
            i = start + (end - start + 1);// 跳过已替换部分
        }

        if (rejected) {
            return SensitiveFilterResult.reject(rawContent);
        }
        if (needAudit) {
            return SensitiveFilterResult.audit(builder.toString());
        }
        if (replaced) {
            return SensitiveFilterResult.replaced(builder.toString());
        }
        return SensitiveFilterResult.pass(rawContent);
    }

    private boolean containsSensitiveChar(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (sensitiveChars.contains(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean bloomMightHit(String text) {
        int maxLen = 32;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            int end = Math.min(length, i + maxLen);
            for (int j = i + 1; j <= end; j++) {
                if (bloomFilter.mightContain(text.substring(i, j))) {
                    return true;
                }
            }
        }
        return false;
    }
}
