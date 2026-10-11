package com.sakana.just_because_meme_understands_you.modules.ai.application.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AiContentHashSupportTest {

    @Test
    void knowledgeBodySha256IgnoresFormattingWhitespaceAndBom() {
        assertEquals(
                AiContentHashSupport.knowledgeBodySha256("\uFEFF规则\r\n\r\n正文  内容"),
                AiContentHashSupport.knowledgeBodySha256("规则 正文\t内容"));
        assertEquals(64, AiContentHashSupport.knowledgeBodySha256("规则正文").length());
    }

    @Test
    void knowledgeBodySha256DependsOnBodyOnly() {
        assertEquals(
                AiContentHashSupport.knowledgeBodySha256("不明觉厉 是什么意思"),
                AiContentHashSupport.knowledgeBodySha256("不明觉厉    是什么意思"));
        assertNotEquals(
                AiContentHashSupport.knowledgeBodySha256("不明觉厉"),
                AiContentHashSupport.knowledgeBodySha256("不明觉厉是什么梗"));
    }
}
