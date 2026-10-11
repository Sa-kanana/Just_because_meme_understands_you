package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeEditRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeResourceItemDTO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.transaction.AfterCommitExecutor;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeEditRevision;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeEditRevisionMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeIndexEventPort;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemePublishAsyncHandler;
import com.sakana.just_because_meme_understands_you.modules.notification.application.NotificationPublisher;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 中文注释：验证修改稿审核的事务保护、内容替换、通知与资源清理规则。 */
class MemeEditRevisionReviewTest {

    private static final String SERVICE_CLASS =
            "com.sakana.just_because_meme_understands_you.modules.meme.application.MemeEditRevisionReviewService";
    private static final String PENDING = "PENDING";

    private MemeMapper memeMapper;
    private MemeEditRevisionMapper revisionMapper;
    private MemeResourceMapper resourceMapper;
    private MemeTagRelationMapper tagRelationMapper;
    private MemeTagMapper tagMapper;
    private NotificationPublisher notificationPublisher;
    private FileAssetService fileAssetService;
    private IUserProfileService userProfileService;
    private IMemeService memeService;
    private MemePublishAsyncHandler memePublishAsyncHandler;
    private MemeIndexEventPort memeIndexEventPort;
    private Object service;
    private Meme official;
    private MemeEditRevision revision;

    @BeforeEach
    void createReviewHarness() throws Exception {
        Class<?> serviceType = loadFeatureClass(SERVICE_CLASS);
        assertNotNull(serviceType, "应新增梗修改稿审核服务");

        memeMapper = mock(MemeMapper.class);
        revisionMapper = mock(MemeEditRevisionMapper.class);
        resourceMapper = mock(MemeResourceMapper.class);
        tagRelationMapper = mock(MemeTagRelationMapper.class);
        tagMapper = mock(MemeTagMapper.class);
        notificationPublisher = mock(NotificationPublisher.class);
        fileAssetService = mock(FileAssetService.class);
        userProfileService = mock(IUserProfileService.class);
        memeService = mock(IMemeService.class);
        memePublishAsyncHandler = mock(MemePublishAsyncHandler.class);
        memeIndexEventPort = mock(MemeIndexEventPort.class);

        service = serviceType.getDeclaredConstructor().newInstance();
        inject(service, "memeMapper", memeMapper);
        inject(service, "memeEditRevisionMapper", revisionMapper);
        inject(service, "memeResourceMapper", resourceMapper);
        inject(service, "memeTagRelationMapper", tagRelationMapper);
        inject(service, "memeTagMapper", tagMapper);
        inject(service, "objectMapper", new ObjectMapper());
        inject(service, "notificationPublisher", notificationPublisher);
        injectIfPresent(service, "fileAssetService", fileAssetService);
        inject(service, "userProfileService", userProfileService);
        inject(service, "memeService", memeService);
        inject(service, "memePublishAsyncHandler", memePublishAsyncHandler);
        inject(service, "memeIndexEventPort", memeIndexEventPort);
        inject(service, "afterCommitExecutor", new AfterCommitExecutor());
    }

    @Test
    void approvalReplacesOnlyEditableContentAndPreservesMemeStateAndCounters() throws Exception {
        prepareRevision(1, PENDING, false);
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tag(2), tag(3)));
        when(memeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(resourceMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        invoke("approve", new Class<?>[]{Long.class, Long.class}, 77L, 90L);

        org.mockito.ArgumentCaptor<Wrapper> memeUpdate = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(memeMapper).update(isNull(), memeUpdate.capture());
        String sqlSet = ((UpdateWrapper<?>) memeUpdate.getValue()).getSqlSet();
        assertEquals(true, sqlSet.contains("name"));
        assertEquals(true, sqlSet.contains("introduction"));
        assertEquals(true, sqlSet.contains("image"));
        assertEquals(false, sqlSet.contains("status"));
        assertEquals(false, sqlSet.contains("likes"));
        assertEquals(false, sqlSet.contains("comments"));
        assertEquals(false, sqlSet.contains("page_views"));
        verify(tagRelationMapper).delete(any(Wrapper.class));
        verify(tagRelationMapper, times(2)).insert(any(MemeTagRelation.class));
        verify(resourceMapper).update(isNull(), any(Wrapper.class));
        verify(resourceMapper, times(2)).insert(any(MemeResource.class));
        verify(fileAssetService).replaceReference("memes/new-cover.png", "MEME", 1001L, "COVER",
                "MEME_REVISION_APPROVED");
        verify(fileAssetService).replaceReference("memes/new-media.png", "MEME_RESOURCE", 301L, "URL",
                "MEME_REVISION_APPROVED");
        verify(fileAssetService).releaseReference("MEME_RESOURCE", 31L, "URL", "MEME_RESOURCES_REPLACED");
        verify(fileAssetService).releaseReference("MEME_REVISION", 77L, "COVER", "MEME_REVISION_APPROVED");
        verify(fileAssetService).releaseReference("MEME_REVISION", 77L, "RESOURCE:0", "MEME_REVISION_APPROVED");
        verify(memePublishAsyncHandler).decrementTagRelatedQuantity(List.of(1));
        verify(memePublishAsyncHandler).incrementTagRelatedQuantity(List.of(2, 3));
        assertReviewNotification("APPROVED", null);
        verify(userProfileService).evictUserMemesCache(21L);
        verify(memeService).evictMemeDetailCache(1001L);
    }

    @Test
    void approvalKeepsOfflineMemeOffline() throws Exception {
        prepareRevision(3, PENDING, false);
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tag(2), tag(3)));
        when(memeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(resourceMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        invoke("approve", new Class<?>[]{Long.class, Long.class}, 77L, 90L);

        org.mockito.ArgumentCaptor<Wrapper> memeUpdate = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(memeMapper).update(isNull(), memeUpdate.capture());
        assertEquals(false, ((UpdateWrapper<?>) memeUpdate.getValue()).getSqlSet().contains("status"));
        verify(memePublishAsyncHandler, never()).decrementTagRelatedQuantity(any());
        verify(memePublishAsyncHandler, never()).incrementTagRelatedQuantity(any());

        org.mockito.ArgumentCaptor<Wrapper> resourceUpdate = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(resourceMapper).update(isNull(), resourceUpdate.capture());
        assertEquals(true, ((AbstractWrapper<?, ?, ?>) resourceUpdate.getValue())
                .getParamNameValuePairs().containsValue(2));
    }

    @Test
    void approvalAllowsRevisionWithoutCover() throws Exception {
        prepareRevision(1, PENDING, false);
        revision.setContentJson(submittedSnapshotWithoutCover());
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tag(2), tag(3)));
        when(memeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(resourceMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        invoke("approve", new Class<?>[]{Long.class, Long.class}, 77L, 90L);

        assertEquals(null, official.getImage());
        org.mockito.ArgumentCaptor<Wrapper> memeUpdate = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(memeMapper).update(isNull(), memeUpdate.capture());
        assertEquals(true, ((UpdateWrapper<?>) memeUpdate.getValue()).getSqlSet().contains("image"));
        verify(fileAssetService).releaseReference("MEME", 1001L, "COVER", "MEME_REVISION_APPROVED");
    }

    @Test
    void rejectionLeavesOfficialContentUntouchedAndNotifiesAuthorWithReason() throws Exception {
        prepareRevision(1, PENDING, false);
        when(memeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(resourceMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        invoke("reject", new Class<?>[]{Long.class, Long.class, String.class}, 77L, 90L, "请更换封面");

        verify(memeMapper, never()).update(any(), any(Wrapper.class));
        verify(resourceMapper, never()).update(any(), any(Wrapper.class));
        verify(tagRelationMapper, never()).delete(any(Wrapper.class));
        assertEquals("REJECTED", revision.getStatus());
        verify(revisionMapper).updateById(any(MemeEditRevision.class));
        assertReviewNotification("REJECTED", "请更换封面");
        verify(fileAssetService).releaseReference("MEME_REVISION", 77L, "COVER", "MEME_REVISION_REJECTED");
        verify(fileAssetService).releaseReference("MEME_REVISION", 77L, "RESOURCE:0", "MEME_REVISION_REJECTED");
    }

    @Test
    void staleBaseHashCannotOverwriteOfficialContent() throws Exception {
        prepareRevision(1, PENDING, true);

        assertThrows(BizException.class,
                () -> invoke("approve", new Class<?>[]{Long.class, Long.class}, 77L, 90L));

        verify(memeMapper, never()).update(any(), any(Wrapper.class));
        verify(revisionMapper, never()).updateById(any(MemeEditRevision.class));
        assertMethodNotInvoked(notificationPublisher, "publishMemeEditReview");
    }

    @Test
    void duplicateReviewActionIsRejected() throws Exception {
        prepareRevision(1, "APPROVED", false);

        assertThrows(BizException.class,
                () -> invoke("approve", new Class<?>[]{Long.class, Long.class}, 77L, 90L));

        verify(memeMapper, never()).update(any(), any(Wrapper.class));
        verify(revisionMapper, never()).updateById(any(MemeEditRevision.class));
    }

    @Test
    void lockedOrPurgedMemeCancelsRevisionWithoutChangingVisibility() throws Exception {
        prepareRevision(5, PENDING, false);
        invoke("approve", new Class<?>[]{Long.class, Long.class}, 77L, 90L);

        verify(memeMapper, never()).update(any(), any(Wrapper.class));
        verify(revisionMapper).updateById(any(MemeEditRevision.class));
        assertReviewNotification("CANCELED", "梗已被锁定，修改稿已取消");
        verify(fileAssetService).releaseReference("MEME_REVISION", 77L, "COVER", "MEME_REVISION_CANCELED");
    }

    private void prepareRevision(int status, String revisionStatus, boolean staleHash) throws Exception {
        official = new Meme().setId(1001L).setUserId(21L).setName("旧名称")
                .setIntroduction("旧介绍").setImage("memes/old-cover.png").setStatus(status)
                .setReleaseTime(LocalDateTime.of(2026, 1, 2, 3, 4))
                .setPageViews(18).setLikes(7).setComments(4);
        revision = new MemeEditRevision();
        revision.setId(77L);
        revision.setMemeId(1001L);
        revision.setEditorUserId(21L);
        revision.setStatus(revisionStatus);
        revision.setCreatedAt(LocalDateTime.of(2026, 1, 3, 4, 5));
        revision.setBaseContentHash(staleHash ? "0".repeat(64) : currentHash());
        revision.setContentJson(submittedSnapshot());
        when(memeMapper.selectOne(any(Wrapper.class))).thenReturn(official);
        when(revisionMapper.selectOne(any(Wrapper.class))).thenReturn(revision);
        when(tagRelationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new MemeTagRelation().setMemeId(1001L).setMemeTagId(1)));
        when(resourceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new MemeResource().setId(31L).setMemeId(1001L).setResourceUrl("memes/old-media.png")
                        .setResourceType(2).setSortOrder(0).setStatus(1)));
        org.mockito.Mockito.when(resourceMapper.insert(any(MemeResource.class))).thenAnswer(invocation -> {
            MemeResource inserted = invocation.getArgument(0);
            if (inserted.getId() == null) {
                inserted.setId(inserted.getResourceUrl().equals("memes/new-media.png") ? 301L : 302L);
            }
            return 1;
        });
        when(revisionMapper.updateById(any(MemeEditRevision.class))).thenAnswer(invocation -> {
            MemeEditRevision updated = invocation.getArgument(0);
            revision.setStatus(updated.getStatus());
            revision.setReviewNote(updated.getReviewNote());
            revision.setReviewedAt(updated.getReviewedAt());
            revision.setReviewedBy(updated.getReviewedBy());
            return 1;
        });
    }

    private String currentHash() throws Exception {
        MemeEditRequestDTO current = new MemeEditRequestDTO();
        current.setName("旧名称");
        current.setIntroduction("旧介绍");
        current.setImage("memes/old-cover.png");
        current.setTagIds(List.of(1));
        current.setResourceUrls(List.of("memes/old-media.png"));
        current.setResources(List.of());
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(new ObjectMapper().writeValueAsBytes(current));
        return HexFormat.of().formatHex(digest);
    }

    private String submittedSnapshot() throws Exception {
        MemeEditRequestDTO submitted = new MemeEditRequestDTO();
        submitted.setName("新名称");
        submitted.setIntroduction("新介绍");
        submitted.setImage("memes/new-cover.png");
        submitted.setTagIds(List.of(2, 3));
        submitted.setResourceUrls(List.of("memes/new-media.png"));
        MemeResourceItemDTO link = new MemeResourceItemDTO();
        link.setType("link");
        link.setTitle("文档");
        link.setUrl("https://example.com/meme");
        link.setSortOrder(0);
        submitted.setResources(List.of(link));
        return new ObjectMapper().writeValueAsString(submitted);
    }

    private String submittedSnapshotWithoutCover() throws Exception {
        MemeEditRequestDTO submitted = new MemeEditRequestDTO();
        submitted.setName("无封面新名称");
        submitted.setIntroduction("新介绍");
        submitted.setTagIds(List.of(2, 3));
        submitted.setResourceUrls(List.of());
        submitted.setResources(List.of());
        return new ObjectMapper().writeValueAsString(submitted);
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = service.getClass().getMethod(methodName, parameterTypes);
        try {
            return method.invoke(service, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = service.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(service, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private static MemeTag tag(int id) {
        return new MemeTag().setId(id).setRelatedQuantity(10);
    }

    private void assertReviewNotification(String expectedStatus, String expectedNote) {
        var invocation = mockingDetails(notificationPublisher).getInvocations().stream()
                .filter(item -> "publishMemeEditReview".equals(item.getMethod().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应写入修改稿审核结果通知"));
        assertEquals(expectedStatus, invocation.getArgument(6));
        assertEquals(expectedNote, invocation.getArgument(7));
    }

    private static void assertMethodNotInvoked(Object mock, String methodName) {
        assertEquals(false, mockingDetails(mock).getInvocations().stream()
                .anyMatch(item -> methodName.equals(item.getMethod().getName())));
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void injectIfPresent(Object target, String fieldName, Object value) throws Exception {
        Field field = org.springframework.util.ReflectionUtils.findField(target.getClass(), fieldName);
        if (field != null) {
            org.springframework.test.util.ReflectionTestUtils.setField(target, fieldName, value);
        }
    }

    private static Class<?> loadFeatureClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
