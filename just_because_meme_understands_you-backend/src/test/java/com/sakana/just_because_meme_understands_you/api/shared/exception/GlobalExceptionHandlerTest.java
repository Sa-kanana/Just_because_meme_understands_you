package com.sakana.just_because_meme_understands_you.api.shared.exception;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingRequiredParameterReturnsBadRequestCodeAndStatus() throws Exception {
        mockMvc.perform(get("/exceptions/missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必填参数：id"));
    }

    @Test
    void unreadableRequestBodyReturnsBadRequestCodeAndStatus() throws Exception {
        mockMvc.perform(post("/exceptions/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求体格式错误，请检查入参"));
    }

    @Test
    void bindingValidationReturnsFirstFieldMessageAndBadRequestStatus() throws Exception {
        mockMvc.perform(post("/exceptions/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("标题不能为空"));
    }

    @Test
    void typeMismatchReturnsBadRequestCodeAndStatus() throws Exception {
        mockMvc.perform(get("/exceptions/type").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数「id」类型不正确"));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowedCodeAndStatus() throws Exception {
        mockMvc.perform(post("/exceptions/method"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }

    @Test
    void accessDeniedReturnsForbiddenCodeAndStatus() throws Exception {
        mockMvc.perform(get("/exceptions/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限访问"));
    }

    @Test
    void illegalArgumentReturnsBadRequestStatus() throws Exception {
        mockMvc.perform(get("/exceptions/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数无效"));
    }

    @Test
    void illegalStateReturnsGenericInternalErrorWithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/exceptions/illegal-state"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }

    @Test
    void unexpectedExceptionReturnsGenericInternalErrorAndKeepsEnvelope() throws Exception {
        mockMvc.perform(get("/exceptions/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void businessHttpCodesSetMatchingStatusAndKeepBodyCode() throws Exception {
        int[][] cases = {{400, 400}, {401, 401}, {403, 403}, {404, 404}, {429, 429}, {1002, 401}};

        for (int[] values : cases) {
            mockMvc.perform(get("/exceptions/business/{code}", values[0]))
                    .andExpect(status().is(values[1]))
                    .andExpect(jsonPath("$.code").value(values[0]));
        }
    }

    @Test
    void businessExceptionCanPreserveCauseForServerDiagnostics() throws Exception {
        IllegalStateException cause = new IllegalStateException("root cause");
        BizException exception = createBusinessException("操作失败", cause);

        assertSame(cause, exception.getCause());
    }

    private BizException createBusinessException(String message, Throwable cause) throws Exception {
        try {
            return BizException.class.getConstructor(String.class, Throwable.class)
                    .newInstance(message, cause);
        } catch (NoSuchMethodException e) {
            fail("BizException must preserve the wrapped cause");
            return null;
        }
    }

    @RestController
    static class ExceptionController {

        @GetMapping("/exceptions/missing")
        void missing(@RequestParam("id") Long id) {
        }

        @PostMapping("/exceptions/body")
        Object body(@RequestBody Object payload) {
            return payload;
        }

        @PostMapping("/exceptions/validation")
        Object validation(@Valid @RequestBody ValidatedBody payload) {
            return payload;
        }

        @GetMapping("/exceptions/type")
        void type(@RequestParam("id") Long id) {
        }

        @GetMapping("/exceptions/method")
        void method() {
        }

        @GetMapping("/exceptions/forbidden")
        void forbidden() {
            throw new AccessDeniedException("restricted resource");
        }

        @GetMapping("/exceptions/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("参数无效");
        }

        @GetMapping("/exceptions/illegal-state")
        void illegalState() {
            throw new IllegalStateException("redis password leaked");
        }

        @GetMapping("/exceptions/unexpected")
        void unexpected() {
            throw new RuntimeException("database credential");
        }

        @GetMapping("/exceptions/business/{code}")
        void business(@PathVariable int code) {
            throw new BizException(code, "request failed");
        }
    }

    static class ValidatedBody {

        @NotBlank(message = "标题不能为空")
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
