package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeEditRevision;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeEditRevisionMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 中文注释：验证作者提交梗修改稿时的所有权、状态、内容校验和审核快照。 */
class MemeEditRevisionSubmitTest {

    private static final String SERVICE_CLASS =
            "com.sakana.just_because_meme_understands_you.modules.meme.application.MemeEditRevisionService";
    private static final String REQUEST_CLASS =
            "com.sakana.just_because_meme_understands_you.api.user.dto.MemeEditRequestDTO";

    private MemeMapper memeMapper;
    private MemeEditRevisionMapper revisionMapper;
    private MemeResourceMapper resourceMapper;
    private MemeTagRelationMapper tagRelationMapper;
    private MemeTagMapper tagMapper;
    private FileAssetService fileAssetService;
    private IUserProfileService userProfileService;
    private Object service;
    private Class<?> requestType;

    @BeforeEach
    void createServiceHarness() throws Exception {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "meme-edit-test"), MemeResource.class);
        Class<?> serviceType = loadFeatureClass(SERVICE_CLASS);
        assertNotNull(serviceType, "应新增作者梗修改稿提交服务");
        requestType = loadFeatureClass(REQUEST_CLASS);
        assertNotNull(requestType, "应新增作者梗修改请求 DTO");

        memeMapper = mock(MemeMapper.class);
        revisionMapper = mock(MemeEditRevisionMapper.class);
        resourceMapper = mock(MemeResourceMapper.class);
        tagRelationMapper = mock(MemeTagRelationMapper.class);
        tagMapper = mock(MemeTagMapper.class);
        fileAssetService = mock(FileAssetService.class);
        userProfileService = mock(IUserProfileService.class);

        service = serviceType.getDeclaredConstructor().newInstance();
        inject(service, "memeMapper", memeMapper);
        inject(service, "memeEditRevisionMapper", revisionMapper);
        inject(service, "memeResourceMapper", resourceMapper);
        inject(service, "memeTagRelationMapper", tagRelationMapper);
        inject(service, "memeTagMapper", tagMapper);
        inject(service, "objectMapper", new ObjectMapper());
        injectIfPresent(service, "fileAssetService", fileAssetService);
        inject(service, "userProfileService", userProfileService);

        when(revisionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(resourceMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(tagRelationMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(new MemeTag().setId(9)));
        when(fileAssetService.promote(anyString(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetService.promoteMemeMediaOrKeepExternal(anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        java.util.concurrent.atomic.AtomicLong revisionIds = new java.util.concurrent.atomic.AtomicLong(700L);
        when(revisionMapper.insert(any(MemeEditRevision.class))).thenAnswer(invocation -> {
            ((MemeEditRevision) invocation.getArgument(0)).setId(revisionIds.getAndIncrement());
            return 1;
        });
    }

    @Test
    void authorCanSubmitRevisionForPublishedAndOfflineMemesWithoutChangingOfficialContent() throws Exception {
        Meme official = meme(1, 21L);
        when(memeMapper.selectOne(any(Wrapper.class))).thenReturn(official, meme(3, 21L));
        Object request = request("新名称", "memes/cover.png", List.of(9), List.of("memes/extra.png"), List.of());

        Object response = invokeSubmit(21L, 1001L, request);
        Object offlineResponse = invokeSubmit(21L, 1001L, request);

        ArgumentCaptor<MemeEditRevision> captor = ArgumentCaptor.forClass(MemeEditRevision.class);
        verify(revisionMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        for (MemeEditRevision revision : captor.getAllValues()) {
            assertEquals(1001L, revision.getMemeId());
            assertEquals(21L, revision.getEditorUserId());
            assertEquals("PENDING", revision.getStatus());
            assertEquals(64, revision.getBaseContentHash().length());
            JsonNode snapshot = new ObjectMapper().readTree(revision.getContentJson());
            assertEquals("新名称", snapshot.path("name").asText());
            assertEquals("memes/cover.png", snapshot.path("image").asText());
        }
        assertEquals("正式内容", official.getName());
        assertEquals("PENDING", getter(response, "status"));
        assertEquals("PENDING", getter(offlineResponse, "status"));
        verify(fileAssetService).replaceReference("memes/cover.png", "MEME_REVISION", 700L, "COVER",
                "MEME_REVISION_CREATED");
        verify(fileAssetService).replaceReference("memes/cover.png", "MEME_REVISION", 701L, "COVER",
                "MEME_REVISION_CREATED");
        verify(fileAssetService).replaceReference("memes/extra.png", "MEME_REVISION", 700L, "RESOURCE:0",
                "MEME_REVISION_CREATED");

        org.mockito.ArgumentCaptor<Wrapper> resourceQueries = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(resourceMapper, org.mockito.Mockito.times(2)).selectList(resourceQueries.capture());
        AbstractWrapper<?, ?, ?> publishedQuery =
                (AbstractWrapper<?, ?, ?>) resourceQueries.getAllValues().get(0);
        AbstractWrapper<?, ?, ?> offlineQuery =
                (AbstractWrapper<?, ?, ?>) resourceQueries.getAllValues().get(1);
        assertEquals(true, publishedQuery.getCustomSqlSegment().contains("status"));
        assertEquals(true, publishedQuery.getParamNameValuePairs().containsValue(1));
        assertEquals(true, offlineQuery.getCustomSqlSegment().contains("status"));
        assertEquals(true, offlineQuery.getParamNameValuePairs().containsValue(0));
    }

    @Test
    void authorCanSubmitRevisionWithoutCover() throws Exception {
        when(memeMapper.selectOne(any(Wrapper.class))).thenReturn(meme(1, 21L));

        Object response = invokeSubmit(21L, 1001L,
                request("无封面梗", null, List.of(), List.of(), List.of()));

        ArgumentCaptor<MemeEditRevision> captor = ArgumentCaptor.forClass(MemeEditRevision.class);
        verify(revisionMapper).insert(captor.capture());
        JsonNode snapshot = new ObjectMapper().readTree(captor.getValue().getContentJson());
        assertEquals(true, snapshot.path("image").isNull() || snapshot.path("image").asText().isBlank());
        assertEquals("PENDING", getter(response, "status"));
        verify(fileAssetService, never()).promote(anyString(), any(), anyString());
    }

    @Test
    void editRevisionReusesPromotedCoverWhenAlsoListedAsMedia() throws Exception {
        String temporaryKey = "tmp/meme/21/shared.png";
        when(memeMapper.selectOne(any(Wrapper.class))).thenReturn(meme(1, 21L));
        when(fileAssetService.promote(temporaryKey, 21L, "meme")).thenReturn("memes/shared.png");

        invokeSubmit(21L, 1001L,
                request("共享图片", temporaryKey, List.of(), List.of(temporaryKey), List.of()));

        verify(fileAssetService, never()).promoteMemeMediaOrKeepExternal(temporaryKey, 21L);
        verify(fileAssetService).replaceReference("memes/shared.png", "MEME_REVISION", 700L, "RESOURCE:0",
                "MEME_REVISION_CREATED");
    }

    @Test
    void nonOwnerCannotSubmitAnEdit() throws Exception {
        when(memeMapper.selectOne(any(Wrapper.class))).thenReturn(meme(1, 21L));

        assertThrows(BizException.class, () -> invokeSubmit(
                22L, 1001L, request("新名称", "memes/cover.png", List.of(), List.of(), List.of())));

        verify(revisionMapper, never()).insert(any(MemeEditRevision.class));
    }

    @Test
    void reviewingLockedAndPurgedStatusesCannotBeEdited() throws Exception {
        for (int status : List.of(2, 4, 5, 6)) {
            when(memeMapper.selectOne(any(Wrapper.class))).thenReturn(meme(status, 21L));
            assertThrows(BizException.class, () -> invokeSubmit(
                    21L, 1001L, request("新名称", "memes/cover.png", List.of(), List.of(), List.of())));
        }
        verify(revisionMapper, never()).insert(any(MemeEditRevision.class));
    }

    @Test
    void duplicatePendingRevisionIsRejected() throws Exception {
        when(memeMapper.selectOne(any(Wrapper.class))).thenReturn(meme(1, 21L));
        when(revisionMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThrows(BizException.class, () -> invokeSubmit(
                21L, 1001L, request("新名称", "memes/cover.png", List.of(), List.of(), List.of())));

        verify(revisionMapper, never()).insert(any(MemeEditRevision.class));
    }

    @Test
    void invalidTagsAndUnsafeLinksAreRejected() throws Exception {
        when(memeMapper.selectOne(any(Wrapper.class))).thenReturn(meme(1, 21L));
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        assertThrows(BizException.class, () -> invokeSubmit(
                21L, 1001L, request("新名称", "memes/cover.png", List.of(99), List.of(), List.of())));

        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(new MemeTag().setId(9)));
        Object invalidLink = resourceItem("javascript", "javascript:alert(1)", "外链");
        assertThrows(BizException.class, () -> invokeSubmit(
                21L, 1001L, request("新名称", "memes/cover.png", List.of(), List.of(), List.of(invalidLink))));

        verify(revisionMapper, never()).insert(any(MemeEditRevision.class));
    }

    private Object invokeSubmit(Long userId, Long memeId, Object request) throws Exception {
        Method submit = service.getClass().getMethod("submitEdit", Long.class, Long.class, requestType);
        try {
            return submit.invoke(service, userId, memeId, request);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private Object request(String name, String image, List<Integer> tagIds,
                           List<String> resourceUrls, List<Object> resources) throws Exception {
        Object request = requestType.getDeclaredConstructor().newInstance();
        set(request, "setName", String.class, name);
        set(request, "setIntroduction", String.class, "修改说明");
        set(request, "setImage", String.class, image);
        set(request, "setTagIds", List.class, tagIds);
        set(request, "setResourceUrls", List.class, resourceUrls);
        set(request, "setResources", List.class, resources);
        return request;
    }

    private static Object resourceItem(String type, String url, String title) throws Exception {
        Class<?> itemType = Class.forName(
                "com.sakana.just_because_meme_understands_you.api.user.dto.MemeResourceItemDTO");
        Object item = itemType.getDeclaredConstructor().newInstance();
        set(item, "setType", String.class, type);
        set(item, "setUrl", String.class, url);
        set(item, "setTitle", String.class, title);
        set(item, "setSortOrder", Integer.class, 0);
        return item;
    }

    private static Meme meme(int status, long ownerId) {
        return new Meme()
                .setId(1001L)
                .setUserId(ownerId)
                .setName("正式内容")
                .setIntroduction("正式介绍")
                .setImage("memes/official.png")
                .setStatus(status);
    }

    private static Object getter(Object target, String name) throws Exception {
        return target.getClass().getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1))
                .invoke(target);
    }

    private static void set(Object target, String method, Class<?> parameterType, Object value) throws Exception {
        target.getClass().getMethod(method, parameterType).invoke(target, value);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Class<?> loadFeatureClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static void injectIfPresent(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = org.springframework.util.ReflectionUtils.findField(target.getClass(), fieldName);
        if (field != null) {
            org.springframework.test.util.ReflectionTestUtils.setField(target, fieldName, value);
        }
    }
}
