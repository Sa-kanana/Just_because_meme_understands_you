package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminMemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCreateResponseVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminMemeService;
import com.sakana.just_because_meme_understands_you.modules.admin.application.impl.AdminMemeServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemePublishService;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 管理端逐条新增梗的接口与应用服务行为测试。 */
class AdminMemeCreateFeatureTest {

    @Test
    void adminMemeControllerExposesAdminOnlyPostCollection() {
        RequestMapping collection = AdminMemeController.class.getAnnotation(RequestMapping.class);
        assertNotNull(collection);
        assertArrayEquals(new String[]{"/admin/v1/memes"}, collection.value());

        PreAuthorize roles = AdminMemeController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(roles);
        assertEquals("hasAnyRole('ADMIN','SUPER_ADMIN')", roles.value());

        Method create = Arrays.stream(AdminMemeController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .findFirst()
                .orElse(null);
        assertNotNull(create, "管理员梗管理应提供 POST 新增接口");
        assertEquals(0, create.getAnnotation(PostMapping.class).value().length);
        assertEquals("AdminMemeCreateRequestDTO", create.getParameterTypes()[0].getSimpleName());
        assertTrue(Arrays.stream(create.getParameterAnnotations()[0])
                .anyMatch(annotation -> annotation.annotationType() == RequestBody.class));
        assertTrue(Arrays.stream(create.getParameterAnnotations()[0])
                .anyMatch(annotation -> annotation.annotationType() == Valid.class));
    }

    @Test
    void adminMemeCreateRequestAllowsMissingCover() {
        AdminMemeCreateRequestDTO request = new AdminMemeCreateRequestDTO();
        request.setName("无封面梗");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .noneMatch(violation -> violation.getPropertyPath().toString().equals("image")));
        }
    }

    @Test
    void adminMemeServicePublishesProvidedFieldsAsCurrentAdmin() throws Exception {
        Method create = findCreateMethod();
        assertNotNull(create, "管理端梗服务应提供逐条创建方法");

        MemeMapper memeMapper = mock(MemeMapper.class);
        MemePublishService publishService = mock(MemePublishService.class);
        doReturn(0L).when(memeMapper).selectCount(any(Wrapper.class));
        MemeCreateResponseVO expected = new MemeCreateResponseVO();
        expected.setMemeId(712L);
        expected.setStatus(2);
        when(publishService.publish(eq(83L), any(MemeCreateRequestDTO.class))).thenReturn(expected);

        AdminMemeServiceImpl service = new AdminMemeServiceImpl();
        ReflectionTestUtils.setField(service, "memeMapper", memeMapper);
        ReflectionTestUtils.setField(service, "memePublishService", publishService);
        Object request = createRequest(create.getParameterTypes()[1]);

        Object actual = create.invoke(service, 83L, request);

        assertSame(expected, actual);
        var captured = org.mockito.ArgumentCaptor.forClass(MemeCreateRequestDTO.class);
        verify(publishService).publish(eq(83L), captured.capture());
        assertEquals("梗名示例", captured.getValue().getName());
        assertEquals("梗的介绍", captured.getValue().getIntroduction());
        assertEquals("tmp/memes/83/20261010/cover.webp", captured.getValue().getImage());
        assertEquals(List.of(3, 8), captured.getValue().getTagIds());
    }

    @Test
    void adminMemeServiceRejectsDuplicateNameBeforePublishing() throws Exception {
        Method create = findCreateMethod();
        assertNotNull(create, "管理端梗服务应提供逐条创建方法");

        MemeMapper memeMapper = mock(MemeMapper.class);
        MemePublishService publishService = mock(MemePublishService.class);
        doReturn(1L).when(memeMapper).selectCount(any(Wrapper.class));
        AdminMemeServiceImpl service = new AdminMemeServiceImpl();
        ReflectionTestUtils.setField(service, "memeMapper", memeMapper);
        ReflectionTestUtils.setField(service, "memePublishService", publishService);
        Object request = createRequest(create.getParameterTypes()[1]);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> create.invoke(service, 83L, request));

        assertInstanceOf(BizException.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("同名"));
        verifyNoInteractions(publishService);
    }

    private static Method findCreateMethod() {
        return Arrays.stream(IAdminMemeService.class.getMethods())
                .filter(method -> method.getName().equals("create"))
                .findFirst()
                .orElse(null);
    }

    private static Object createRequest(Class<?> requestType) throws Exception {
        assertEquals("AdminMemeCreateRequestDTO", requestType.getSimpleName());
        Object request = requestType.getConstructor().newInstance();
        requestType.getMethod("setName", String.class).invoke(request, "梗名示例");
        requestType.getMethod("setIntroduction", String.class).invoke(request, "梗的介绍");
        requestType.getMethod("setImage", String.class)
                .invoke(request, "tmp/memes/83/20261010/cover.webp");
        requestType.getMethod("setTagIds", List.class).invoke(request, List.of(3, 8));
        return request;
    }
}
