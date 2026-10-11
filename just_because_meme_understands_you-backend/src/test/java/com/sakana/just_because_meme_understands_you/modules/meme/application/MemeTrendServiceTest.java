package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTrendResponseVO;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.MemeTrendRepository;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemeTrendServiceTest {

    @Test
    void trendItemsExposePublicImageUrlsInsteadOfOssObjectKeys() {
        MemeTrendRepository repository = mock(MemeTrendRepository.class);
        OssUrlHelper ossUrlHelper = new OssUrlHelper();
        ReflectionTestUtils.setField(ossUrlHelper, "bucketName", "meme-bucket");
        ReflectionTestUtils.setField(ossUrlHelper, "endpoint", "oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(ossUrlHelper, "uploadHost", "https://upload.example.com");
        ReflectionTestUtils.setField(ossUrlHelper, "publicBaseUrl", "https://cdn.example.com");
        ReflectionTestUtils.invokeMethod(ossUrlHelper, "init");

        when(repository.topMemes(any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(List.of(new MemeTrendRepository.TrendRow(
                        1L, "奶蛙", "memes/cover.jpg", 10, 2, 1, 24, "抽象")));
        when(repository.dailyTotals(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(repository.findWeeklyTopic(anyInt(), anyInt()))
                .thenReturn(Optional.of(new MemeTrendRepository.WeeklyTopicRow(1, "抽象", 10, false)));

        MemeTrendResponseVO response = new MemeTrendService(repository, ossUrlHelper).getTrends(7);

        assertEquals("https://cdn.example.com/memes/cover.jpg", response.topMemes().get(0).image());
    }
}
