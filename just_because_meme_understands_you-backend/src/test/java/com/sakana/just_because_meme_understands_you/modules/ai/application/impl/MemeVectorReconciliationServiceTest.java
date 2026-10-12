package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MemeVectorReconciliationServiceTest {

    @Test
    void requiredReconciliationTypesArePresent() {
        List<String> requiredTypes = List.of(
                "com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.MemeVectorReconciliationJob",
                "com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.MemeVectorReconciliationJobMapper",
                "com.sakana.just_because_meme_understands_you.modules.ai.application.MemeVectorReconciliationService",
                "com.sakana.just_because_meme_understands_you.modules.ai.application.impl.MemeVectorReconciliationWorker",
                "com.sakana.just_because_meme_understands_you.api.admin.controller.AiVectorReconciliationController",
                "com.sakana.just_because_meme_understands_you.api.admin.vo.AiVectorReconciliationJobVO");

        for (String className : requiredTypes) {
            assertDoesNotThrow(() -> Class.forName(className), className);
        }
    }
}
