package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.MemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssObjectPromoteService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 中文注释：验证用户可在不上传封面时提交新梗。 */
class MemePublishServiceTest {

    private MemeMapper memeMapper;
    private OssObjectPromoteService ossObjectPromoteService;
    private MemePublishService service;

    @BeforeEach
    void createServiceHarness() throws Exception {
        memeMapper = mock(MemeMapper.class);
        MemeResourceMapper resourceMapper = mock(MemeResourceMapper.class);
        MemeTagRelationMapper tagRelationMapper = mock(MemeTagRelationMapper.class);
        MemeTagMapper tagMapper = mock(MemeTagMapper.class);
        MemeBloomFilterService bloomFilterService = mock(MemeBloomFilterService.class);
        IUserProfileService userProfileService = mock(IUserProfileService.class);
        ossObjectPromoteService = mock(OssObjectPromoteService.class);

        service = new MemePublishService();
        inject("memeMapper", memeMapper);
        inject("memeResourceMapper", resourceMapper);
        inject("memeTagRelationMapper", tagRelationMapper);
        inject("memeTagMapper", tagMapper);
        inject("memeBloomFilterService", bloomFilterService);
        inject("userProfileService", userProfileService);
        inject("ossObjectPromoteService", ossObjectPromoteService);
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
        verify(ossObjectPromoteService, never()).promoteMemeImage(anyString(), anyLong());
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = MemePublishService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
