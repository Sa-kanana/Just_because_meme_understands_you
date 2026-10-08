package com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.filter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveWordEngineTest {

    @Test
    void matchesWordsLongerThanBloomWindow() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        String longWord = "longword-".repeat(5);
        engine.rebuild(Map.of(longWord, 1));

        SensitiveFilterResult result = engine.filter("前缀" + longWord + "后缀");

        assertEquals("前缀" + "*".repeat(longWord.length()) + "后缀", result.getContent());
        assertFalse(result.isRejected());
        assertFalse(result.isNeedAudit());
    }

    @Test
    void prefersLongestMatchAtSameStartAndUsesItsAction() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(Map.of("a", 1, "ab", 2));

        SensitiveFilterResult result = engine.filter("ab");

        assertEquals("**", result.getContent());
        assertTrue(result.isNeedAudit());
        assertFalse(result.isRejected());
    }

    @Test
    void rejectReturnsOriginalContentEvenAfterEarlierReplacement() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(Map.of("foo", 1, "bar", 3));

        SensitiveFilterResult result = engine.filter("foo bar");

        assertEquals("foo bar", result.getContent());
        assertTrue(result.isRejected());
        assertFalse(result.isNeedAudit());
    }

    @Test
    void trimsWordsAndKeepsTheHighestActionForDuplicates() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(Map.of("danger", 1, " danger ", 3));

        SensitiveFilterResult result = engine.filter("danger");

        assertEquals("danger", result.getContent());
        assertTrue(result.isRejected());
    }

    @Test
    void doesNotIgnorePunctuationWhenMatchingWords() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(Map.of("ab", 1));

        SensitiveFilterResult result = engine.filter("a.b");

        assertEquals("a.b", result.getContent());
        assertFalse(result.isRejected());
        assertFalse(result.isNeedAudit());
    }

    @Test
    void matchesPunctuationWhenItIsPartOfTheDictionaryWord() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(Map.of("a.b", 1));

        SensitiveFilterResult result = engine.filter("a.b");

        assertEquals("***", result.getContent());
        assertFalse(result.isRejected());
    }
}
