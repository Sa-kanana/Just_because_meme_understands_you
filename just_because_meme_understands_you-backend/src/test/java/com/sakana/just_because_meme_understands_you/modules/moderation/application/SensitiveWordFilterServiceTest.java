package com.sakana.just_because_meme_understands_you.modules.moderation.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveLevel;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveScene;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveWordRule;
import com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.filter.SensitiveWordEngine;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SensitiveWordFilterServiceTest {

    @Test
    void reviewLevelPassesOnlyForContentThatAlreadyHasAReviewQueue() {
        SensitiveWordFilterService service = serviceWith(rule("check", SensitiveLevel.REVIEW, null));

        assertEquals("check", service.filterForReviewableContent("check", SensitiveScene.MEME_TITLE));
        assertThrows(BizException.class,
                () -> service.filterForImmediateContent("check", SensitiveScene.USER_NICKNAME));
        assertThrows(BizException.class,
                () -> service.filterForReviewableContent("check", SensitiveScene.COMMENT));
        assertThrows(BizException.class, () -> service.filterForInsert("check"));
    }

    @Test
    void blockLevelIsRejectedInReviewableContentScenes() {
        SensitiveWordFilterService service = serviceWith(rule("block", SensitiveLevel.BLOCK, null));

        assertThrows(BizException.class,
                () -> service.filterForReviewableContent("block", SensitiveScene.MEME_DESCRIPTION));
    }

    @Test
    void normalLevelAppliesConfiguredReplacement() {
        SensitiveWordFilterService service = serviceWith(rule("mask", SensitiveLevel.NORMAL, "[已替换]"));

        assertEquals("标题[已替换]", service.filterForReviewableContent("标题mask", SensitiveScene.MEME_TITLE));
    }

    private static SensitiveWordFilterService serviceWith(SensitiveWordRule rule) {
        SensitiveWordFilterService service = new SensitiveWordFilterService();
        SensitiveWordEngine engine = (SensitiveWordEngine) ReflectionTestUtils.getField(service, "engine");
        engine.rebuild(List.of(rule), 1L);
        return service;
    }

    private static SensitiveWordRule rule(String word, SensitiveLevel level, String replacement) {
        return new SensitiveWordRule(word, "CUSTOM", level, replacement);
    }
}
