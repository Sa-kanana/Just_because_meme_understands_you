package com.sakana.just_because_meme_understands_you.api.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiVectorReconciliationRouteContractTest {

    @Test
    void adminRoutesRequireAdminRole() {
        PreAuthorize authorization = AiVectorReconciliationController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertTrue(authorization.value().contains("ADMIN"));
    }

    @Test
    void startReturnsActiveJobStatus() throws Exception {
        Method method = AiVectorReconciliationController.class.getMethod("start", jakarta.servlet.http.HttpServletRequest.class);
        assertNotNull(method.getAnnotation(PostMapping.class));
        assertTrue(method.getGenericReturnType().getTypeName().contains("AiVectorReconciliationJobVO"));
    }

    @Test
    void findReturnsTheRequestedJob() throws Exception {
        Method method = AiVectorReconciliationController.class.getMethod(
                "find", long.class, jakarta.servlet.http.HttpServletRequest.class);
        assertEquals("/{jobId}", method.getAnnotation(GetMapping.class).value()[0]);
    }

    @Test
    void resumeRouteTargetsFailedJobs() throws Exception {
        Method method = AiVectorReconciliationController.class.getMethod(
                "resume", long.class, jakarta.servlet.http.HttpServletRequest.class);
        assertEquals("/{jobId}/resume", method.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("/admin/v1/ai/vector-index/reconciliation-jobs",
                AiVectorReconciliationController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
}
