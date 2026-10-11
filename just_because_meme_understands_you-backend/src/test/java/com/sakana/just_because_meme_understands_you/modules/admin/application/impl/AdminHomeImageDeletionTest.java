package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.modules.home.application.IHomeImageService;
import com.sakana.just_because_meme_understands_you.modules.home.domain.entity.HomeImage;
import com.sakana.just_because_meme_understands_you.modules.home.infrastructure.persistence.mapper.HomeImageMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssObjectPromoteService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证管理端删除轮播图时会安排对应 OSS 对象清理。 */
class AdminHomeImageDeletionTest {

    @Test
    void deleteRemovesStoredCarouselObjectFromOss() throws Exception {
        String objectKey = "home/20261010/banner.png";
        HomeImage image = new HomeImage();
        image.setId(7L);
        image.setImgUrl(objectKey);

        HomeImageMapper homeImageMapper = mock(HomeImageMapper.class);
        when(homeImageMapper.selectById(7L)).thenReturn(image);
        IHomeImageService homeImageService = mock(IHomeImageService.class);
        OssObjectPromoteService ossObjectPromoteService = mock(OssObjectPromoteService.class);
        MemeCatalogPort memeCatalogPort = mock(MemeCatalogPort.class);

        AdminHomeImageServiceImpl service = new AdminHomeImageServiceImpl();
        ReflectionTestUtils.setField(service, "homeImageMapper", homeImageMapper);
        ReflectionTestUtils.setField(service, "homeImageService", homeImageService);
        ReflectionTestUtils.setField(service, "ossObjectPromoteService", ossObjectPromoteService);
        ReflectionTestUtils.setField(service, "ossUrlHelper", new OssUrlHelper());
        ReflectionTestUtils.setField(service, "memeCatalogPort", memeCatalogPort);

        service.delete(7L);

        verify(ossObjectPromoteService).deleteHomeImage(objectKey);
        verify(homeImageMapper).deleteById(7L);
        verify(homeImageService).evictCarouselCache();
    }

    @Test
    void ossFailureKeepsCarouselRecordAndCache() throws Exception {
        String objectKey = "home/20261010/banner.png";
        HomeImage image = new HomeImage();
        image.setId(7L);
        image.setImgUrl(objectKey);

        HomeImageMapper homeImageMapper = mock(HomeImageMapper.class);
        when(homeImageMapper.selectById(7L)).thenReturn(image);
        IHomeImageService homeImageService = mock(IHomeImageService.class);
        OssObjectPromoteService ossObjectPromoteService = mock(OssObjectPromoteService.class, invocation -> {
            if ("deleteHomeImage".equals(invocation.getMethod().getName())) {
                throw new BizException(500, "OSS 删除失败");
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        MemeCatalogPort memeCatalogPort = mock(MemeCatalogPort.class);

        AdminHomeImageServiceImpl service = new AdminHomeImageServiceImpl();
        ReflectionTestUtils.setField(service, "homeImageMapper", homeImageMapper);
        ReflectionTestUtils.setField(service, "homeImageService", homeImageService);
        ReflectionTestUtils.setField(service, "ossObjectPromoteService", ossObjectPromoteService);
        ReflectionTestUtils.setField(service, "ossUrlHelper", new OssUrlHelper());
        ReflectionTestUtils.setField(service, "memeCatalogPort", memeCatalogPort);

        assertThrows(BizException.class, () -> service.delete(7L));

        verifyNoInteractions(homeImageService);
        verify(homeImageMapper).selectById(7L);
        verify(homeImageMapper, never()).deleteById(7L);
    }

    @Test
    void retainsOssObjectWhenMemeStillReferencesIt() {
        String objectKey = "home/20261010/banner.png";
        HomeImage image = new HomeImage();
        image.setId(7L);
        image.setImgUrl(objectKey);

        HomeImageMapper homeImageMapper = mock(HomeImageMapper.class);
        when(homeImageMapper.selectById(7L)).thenReturn(image);
        IHomeImageService homeImageService = mock(IHomeImageService.class);
        OssObjectPromoteService ossObjectPromoteService = mock(OssObjectPromoteService.class);
        MemeCatalogPort memeCatalogPort = mock(MemeCatalogPort.class);
        when(memeCatalogPort.isImageAssetReferenced(objectKey)).thenReturn(true);

        AdminHomeImageServiceImpl service = new AdminHomeImageServiceImpl();
        ReflectionTestUtils.setField(service, "homeImageMapper", homeImageMapper);
        ReflectionTestUtils.setField(service, "homeImageService", homeImageService);
        ReflectionTestUtils.setField(service, "ossObjectPromoteService", ossObjectPromoteService);
        ReflectionTestUtils.setField(service, "ossUrlHelper", new OssUrlHelper());
        ReflectionTestUtils.setField(service, "memeCatalogPort", memeCatalogPort);

        service.delete(7L);

        verify(homeImageMapper).deleteById(7L);
        verifyNoInteractions(ossObjectPromoteService);
        verify(homeImageService).evictCarouselCache();
    }

    @Test
    void retainsOssObjectWhenAnotherCarouselUsesIt() {
        String objectKey = "home/20261010/banner.png";
        HomeImage image = new HomeImage();
        image.setId(7L);
        image.setImgUrl(objectKey);

        HomeImageMapper homeImageMapper = mock(HomeImageMapper.class);
        when(homeImageMapper.selectById(7L)).thenReturn(image);
        when(homeImageMapper.selectCount(any())).thenReturn(1L);
        IHomeImageService homeImageService = mock(IHomeImageService.class);
        OssObjectPromoteService ossObjectPromoteService = mock(OssObjectPromoteService.class);
        MemeCatalogPort memeCatalogPort = mock(MemeCatalogPort.class);

        AdminHomeImageServiceImpl service = new AdminHomeImageServiceImpl();
        ReflectionTestUtils.setField(service, "homeImageMapper", homeImageMapper);
        ReflectionTestUtils.setField(service, "homeImageService", homeImageService);
        ReflectionTestUtils.setField(service, "ossObjectPromoteService", ossObjectPromoteService);
        ReflectionTestUtils.setField(service, "ossUrlHelper", new OssUrlHelper());
        ReflectionTestUtils.setField(service, "memeCatalogPort", memeCatalogPort);

        service.delete(7L);

        verify(homeImageMapper).deleteById(7L);
        verifyNoInteractions(ossObjectPromoteService);
        verify(homeImageService).evictCarouselCache();
    }

    @Test
    void ossCleanupDeletesOnlySafeHomeImageKeys() throws Exception {
        String objectKey = "home/20261010/banner.png";
        OSS ossClient = mock(OSS.class);
        OssObjectPromoteService cleanupService = new OssObjectPromoteService();
        ReflectionTestUtils.setField(cleanupService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(cleanupService, "ossClient", ossClient);
        ReflectionTestUtils.setField(cleanupService, "ossUrlHelper", new OssUrlHelper());
        cleanupService.deleteHomeImage(objectKey);
        assertThrows(BizException.class, () -> cleanupService.deleteHomeImage("memes/20261010/meme.png"));
        assertThrows(BizException.class, () -> cleanupService.deleteHomeImage("home/../outside.png"));

        verify(ossClient).deleteObject("test-bucket", objectKey);
    }
}
