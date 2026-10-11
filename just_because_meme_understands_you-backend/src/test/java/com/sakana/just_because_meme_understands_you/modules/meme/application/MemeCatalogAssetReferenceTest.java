package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeEditRevision;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.config.MemeCrawlProperties;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeEditRevisionMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证梗图模块会保护仍被梗图内容引用的 OSS 图片。 */
class MemeCatalogAssetReferenceTest {

    private MemeMapper memeMapper;
    private MemeEditRevisionMapper revisionMapper;
    private MemeResourceMapper resourceMapper;
    private MemeCatalogApplicationAdapter adapter;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "meme-reference-test"), Meme.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "resource-reference-test"), MemeResource.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "revision-reference-test"), MemeEditRevision.class);
        memeMapper = mock(MemeMapper.class);
        revisionMapper = mock(MemeEditRevisionMapper.class);
        resourceMapper = mock(MemeResourceMapper.class);
        adapter = new MemeCatalogApplicationAdapter(
                memeMapper,
                revisionMapper,
                mock(MemeTagMapper.class),
                mock(MemeTagRelationMapper.class),
                resourceMapper,
                mock(MemeBloomFilterService.class),
                mock(MemeCrawlProperties.class));
    }

    @Test
    void recognizesAnExistingMemeCoverReference() {
        when(memeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertTrue(isReferenced("home/20261010/banner.png"));
    }

    @Test
    void recognizesAnExistingMemeResourceReference() {
        when(memeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(resourceMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertTrue(isReferenced("home/20261010/banner.png"));
    }

    @Test
    void recognizesAReferenceInPendingMemeEdit() {
        when(memeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(resourceMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(revisionMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertTrue(isReferenced("home/20261010/banner.png"));
    }

    @Test
    void reportsUnreferencedImageWhenNoMemeUsesIt() {
        when(memeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(resourceMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(revisionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertFalse(isReferenced("home/20261010/banner.png"));
    }

    private boolean isReferenced(String objectKey) {
        return adapter.isImageAssetReferenced(objectKey);
    }
}
