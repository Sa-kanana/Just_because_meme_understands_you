package com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.filter;

import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveLevel;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveWordRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveWordEngineTest {

    @Test
    void matchesWordsLongerThanBloomWindow() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        String longWord = "longword-".repeat(5);
        engine.rebuild(List.of(rule(longWord, SensitiveLevel.NORMAL, "*")), 7L);

        SensitiveFilterResult result = engine.filter("前缀" + longWord + "后缀");

        assertEquals("前缀*后缀", result.getContent());
        assertFalse(result.isRejected());
        assertFalse(result.isNeedAudit());
        assertEquals(7L, engine.version());
    }

    @Test
    void unicodeCompatibilityCharactersAreMatchedAndReplacementUsesOriginalIndices() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(List.of(rule("ab", SensitiveLevel.NORMAL, "***")), 1L);

        SensitiveFilterResult result = engine.filter("前ＡＢ后");

        assertEquals("前***后", result.getContent());
        assertEquals(1, result.getMatches().get(0).start());
        assertEquals(2, result.getMatches().get(0).end());
    }

    @Test
    void zeroWidthCharactersCannotBypassMatchingAndAreIncludedInReplacementSpan() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(List.of(rule("ab", SensitiveLevel.NORMAL, "x")), 1L);

        SensitiveFilterResult result = engine.filter("a\u200Bb");

        assertEquals("x", result.getContent());
        assertEquals(1, result.getMatches().size());
    }

    @Test
    void reviewResultKeepsOriginalTextForTheModerationQueue() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(List.of(
                rule("ab", SensitiveLevel.REVIEW, null),
                rule("mask", SensitiveLevel.NORMAL, "[已替换]")), 1L);

        SensitiveFilterResult result = engine.filter("mask prefix ab suffix");

        assertEquals(SensitiveFilterResult.Decision.REVIEW, result.getDecision());
        assertEquals("mask prefix ab suffix", result.getContent());
        assertTrue(result.isNeedAudit());
        assertFalse(result.isRejected());
        assertEquals("CUSTOM", result.getMatches().get(0).category());
    }

    @Test
    void blockDecisionKeepsOriginalTextAndProvidesStructuredHitDetails() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(List.of(rule("danger", SensitiveLevel.BLOCK, null)), 1L);

        SensitiveFilterResult result = engine.filter("prefix danger suffix");

        assertEquals(SensitiveFilterResult.Decision.BLOCK, result.getDecision());
        assertEquals("prefix danger suffix", result.getContent());
        assertTrue(result.isRejected());
        assertEquals(1, result.getMatches().size());
        assertEquals("danger", result.getMatches().get(0).word());
        assertEquals(7, result.getMatches().get(0).start());
        assertEquals(12, result.getMatches().get(0).end());
    }

    @Test
    void blockRuleIsNotHiddenByAnOverlappingLowerRiskRule() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(List.of(
                rule("ab", SensitiveLevel.NORMAL, "x"),
                rule("b", SensitiveLevel.BLOCK, null)), 1L);

        SensitiveFilterResult result = engine.filter("ab");

        assertEquals(SensitiveFilterResult.Decision.BLOCK, result.getDecision());
    }

    @Test
    void doesNotIgnorePunctuationWhenMatchingWords() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(List.of(rule("ab", SensitiveLevel.NORMAL, "*")), 1L);

        SensitiveFilterResult result = engine.filter("a.b");

        assertEquals("a.b", result.getContent());
        assertTrue(result.getMatches().isEmpty());
    }

    @Test
    void matchesPunctuationWhenItIsPartOfTheConfiguredWord() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(List.of(rule("a.b", SensitiveLevel.NORMAL, "***")), 1L);

        SensitiveFilterResult result = engine.filter("a.b");

        assertEquals("***", result.getContent());
    }

    private static SensitiveWordRule rule(String word, SensitiveLevel level, String replacement) {
        return new SensitiveWordRule(word, "CUSTOM", level, replacement);
    }
}
