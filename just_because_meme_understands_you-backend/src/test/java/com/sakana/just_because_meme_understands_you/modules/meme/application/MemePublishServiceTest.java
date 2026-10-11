package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.MemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 中文注释：验证用户可在不上传封面时提交新梗。 */
class MemePublishServiceTest {

    private MemeMapper memeMapper;
    private MemeResourceMapper memeResourceMapper;
    private FileAssetService fileAssetService;
    private MemePublishService service;

    @BeforeEach
    void createServiceHarness() throws Exception {
        memeMapper = mock(MemeMapper.class);
        memeResourceMapper = mock(MemeResourceMapper.class);
        MemeTagRelationMapper tagRelationMapper = mock(MemeTagRelationMapper.class);
        MemeTagMapper tagMapper = mock(MemeTagMapper.class);
        MemeBloomFilterService bloomFilterService = mock(MemeBloomFilterService.class);
        IUserProfileService userProfileService = mock(IUserProfileService.class);
        fileAssetService = mock(FileAssetService.class);

        service = new MemePublishService();
        inject("memeMapper", memeMapper);
        inject("memeResourceMapper", memeResourceMapper);
        inject("memeTagRelationMapper", tagRelationMapper);
        inject("memeTagMapper", tagMapper);
        inject("memeBloomFilterService", bloomFilterService);
        inject("userProfileService", userProfileService);
        injectIfPresent("fileAssetService", fileAssetService);
    }

    @Test
    void publishingMemeWithoutCoverStoresNullImage() {
        when(memeMapper.insert(any(Meme.class))).thenAnswer(invocation -> {
            Meme meme = invocation.getArgument(0);
            meme.setId(501L);
            return 1;
        });
        MemeCreateRequestDTO request = new MemeCreateRequestDTO();
        request.setName("无封面梗");

        assertNotNull(service.publish(21L, request));

        ArgumentCaptor<Meme> captor = ArgumentCaptor.forClass(Meme.class);
        verify(memeMapper).insert(captor.capture());
        assertNull(captor.getValue().getImage());
        verify(fileAssetService, never()).promote(anyString(), anyLong(), anyString());
    }

    @Test
    void publishingBindsTrackedCoverAndMediaResourceByPersistedRowId() {
        when(fileAssetService.promote("tmp/meme/21/cover.png", 21L, "meme"))
                .thenReturn("memes/cover.png");
        when(fileAssetService.promoteMemeMediaOrKeepExternal("tmp/meme/21/media.png", 21L))
                .thenReturn("memes/media.png");
        when(memeMapper.insert(any(Meme.class))).thenAnswer(invocation -> {
            ((Meme) invocation.getArgument(0)).setId(501L);
            return 1;
        });
        when(memeResourceMapper.insert(any(MemeResource.class))).thenAnswer(invocation -> {
            ((MemeResource) invocation.getArgument(0)).setId(902L);
            return 1;
        });
        MemeCreateRequestDTO request = new MemeCreateRequestDTO();
        request.setName("带图梗");
        request.setImage("tmp/meme/21/cover.png");
        request.setResourceUrls(List.of("tmp/meme/21/media.png"));

        service.publish(21L, request);

        verify(fileAssetService).replaceReference("memes/cover.png", "MEME", 501L, "COVER", "MEME_PUBLISHED");
        verify(fileAssetService).replaceReference("memes/media.png", "MEME_RESOURCE", 902L, "URL",
                "MEME_RESOURCE_CREATED");
    }

    @Test
    void publishingReusesOnePromotionWhenCoverIsAlsoAMediaResource() {
        String temporaryKey = "tmp/meme/21/shared.png";
        when(fileAssetService.promote(temporaryKey, 21L, "meme")).thenReturn("memes/shared.png");
        when(memeMapper.insert(any(Meme.class))).thenAnswer(invocation -> {
            ((Meme) invocation.getArgument(0)).setId(502L);
            return 1;
        });
        when(memeResourceMapper.insert(any(MemeResource.class))).thenAnswer(invocation -> {
            ((MemeResource) invocation.getArgument(0)).setId(903L);
            return 1;
        });
        MemeCreateRequestDTO request = new MemeCreateRequestDTO();
        request.setName("共享图片梗");
        request.setImage(temporaryKey);
        request.setResourceUrls(List.of(temporaryKey));

        service.publish(21L, request);

        verify(fileAssetService).promote(temporaryKey, 21L, "meme");
        verify(fileAssetService, never()).promoteMemeMediaOrKeepExternal(temporaryKey, 21L);
        verify(fileAssetService).replaceReference("memes/shared.png", "MEME_RESOURCE", 903L, "URL",
                "MEME_RESOURCE_CREATED");
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = MemePublishService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private void injectIfPresent(String fieldName, Object value) throws Exception {
        try {
            inject(fieldName, value);
        } catch (NoSuchFieldException ignored) {
            // 中文注释：当前测试在实现前允许缺少新依赖字段，以便通过行为断言呈现红灯。
        }
    }
}
