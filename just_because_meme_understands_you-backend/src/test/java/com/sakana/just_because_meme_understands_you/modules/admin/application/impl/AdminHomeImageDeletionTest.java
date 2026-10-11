package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.sakana.just_because_meme_understands_you.modules.home.application.IHomeImageService;
import com.sakana.just_because_meme_understands_you.modules.home.domain.entity.HomeImage;
import com.sakana.just_because_meme_understands_you.modules.home.infrastructure.persistence.mapper.HomeImageMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssObjectPromoteService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 中文注释：验证删除首页图时只释放业务引用，不在业务事务中同步访问OSS。 */
class AdminHomeImageDeletionTest {

    @Test
    void deleteReleasesTrackedImageReferenceWithoutCallingOss() {
        String objectKey = "home/20261010/banner.png";
        HomeImage image = new HomeImage();
        image.setId(7L);
        image.setImgUrl(objectKey);

        HomeImageMapper homeImageMapper = mock(HomeImageMapper.class);
        when(homeImageMapper.selectById(7L)).thenReturn(image);
        when(homeImageMapper.deleteById(7L)).thenReturn(1);
        IHomeImageService homeImageService = mock(IHomeImageService.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        OssObjectPromoteService obsoleteOssService = mock(OssObjectPromoteService.class);
        MemeCatalogPort memeCatalogPort = mock(MemeCatalogPort.class);

        AdminHomeImageServiceImpl service = new AdminHomeImageServiceImpl();
        ReflectionTestUtils.setField(service, "homeImageMapper", homeImageMapper);
        ReflectionTestUtils.setField(service, "homeImageService", homeImageService);
        setFieldIfPresent(service, "fileAssetService", fileAssetService);
        setFieldIfPresent(service, "ossObjectPromoteService", obsoleteOssService);
        setFieldIfPresent(service, "memeCatalogPort", memeCatalogPort);
        ReflectionTestUtils.setField(service, "ossUrlHelper", new OssUrlHelper());

        service.delete(7L);

        verify(fileAssetService).releaseReference("BANNER", 7L, "IMAGE", "BANNER_DELETED");
        verify(homeImageMapper).deleteById(7L);
        verify(homeImageService).evictCarouselCache();
        verifyNoInteractions(obsoleteOssService);
        verifyNoInteractions(memeCatalogPort);
    }

    private void setFieldIfPresent(Object target, String fieldName, Object value) {
        if (org.springframework.util.ReflectionUtils.findField(target.getClass(), fieldName) != null) {
            ReflectionTestUtils.setField(target, fieldName, value);
        }
    }
}
