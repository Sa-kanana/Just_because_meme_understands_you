package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.sakana.just_because_meme_understands_you.modules.meme.application.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeIndexEventPort;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MemeDeleteServiceAdminTest {

    @BeforeEach
    void initializeMybatisEntityMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MemeResource.class);
    }

    @Mock
    private MemeMapper memeMapper;

    @Mock
    private MemeResourceMapper memeResourceMapper;

    @Mock
    private MemeTagRelationMapper memeTagRelationMapper;

    @Mock
    private MemePublishAsyncHandler memePublishAsyncHandler;

    @Mock
    private MemeOssCleanupHandler memeOssCleanupHandler;

    @Mock
    private IUserProfileService userProfileService;

    @Mock
    private IMemeService memeService;

    @Mock
    private MemeIndexEventPort memeIndexEventPort;

    @InjectMocks
    private MemeDeleteService service;

    @Test
    void adminCanPurgeMemeRegardlessOfOwnerAndCurrentStatus() {
        Meme meme = meme(73L, MemeVisibilitySupport.STATUS_NORMAL);
        when(memeMapper.selectByIdForUpdate(73L)).thenReturn(meme);
        when(memeTagRelationMapper.selectList(any()))
                .thenReturn(java.util.List.of(new MemeTagRelation().setMemeTagId(8)));

        Meme deleted = service.purgeByAdmin(73L);

        assertEquals(MemeVisibilitySupport.STATUS_PURGED, deleted.getStatus());
        assertNotNull(deleted.getPurgedAt());
        verify(memeMapper).selectByIdForUpdate(73L);
        verify(memeMapper).updateById(meme);
        verify(memeIndexEventPort).requestRemoval(73L);
        verify(memePublishAsyncHandler).decrementTagRelatedQuantity(java.util.List.of(8));
        verify(memeResourceMapper).update(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
        verify(memeOssCleanupHandler).cleanupMemeAssets(org.mockito.ArgumentMatchers.any(Meme.class));
    }

    @Test
    void adminPurgeIsIdempotentForAlreadyPurgedMeme() {
        Meme meme = meme(73L, MemeVisibilitySupport.STATUS_PURGED);
        when(memeMapper.selectByIdForUpdate(73L)).thenReturn(meme);

        Meme deleted = service.purgeByAdmin(73L);

        assertEquals(MemeVisibilitySupport.STATUS_PURGED, deleted.getStatus());
        verify(memeMapper, never()).updateById(meme);
        verify(memeIndexEventPort, never()).requestRemoval(73L);
        verify(memeOssCleanupHandler, never()).cleanupMemeAssets(org.mockito.ArgumentMatchers.any(Meme.class));
    }

    private Meme meme(Long id, Integer status) {
        return new Meme().setId(id).setUserId(19L).setStatus(status).setImage("meme-image");
    }
}
