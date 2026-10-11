package com.sakana.just_because_meme_understands_you.modules.user.application.profile;

import com.sakana.just_because_meme_understands_you.api.user.vo.UserProfileStatsVO;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.user.application.favorite.UserFavoriteCountService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.impl.UserProfileServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserStats;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserStatsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserProfileServiceStatsTest {

    @Test
    void countsPublishedMemesFromMemeCatalogEvenWhenStatsRowIsMissing() {
        UserStatsMapper statsMapper = mock(UserStatsMapper.class);
        MemeCatalogPort memeCatalog = mock(MemeCatalogPort.class);
        UserFavoriteCountService favoriteCountService = mock(UserFavoriteCountService.class);
        UserProfileServiceImpl service = new UserProfileServiceImpl();
        ReflectionTestUtils.setField(service, "userStatsMapper", statsMapper);
        ReflectionTestUtils.setField(service, "memeCatalogPort", memeCatalog);
        ReflectionTestUtils.setField(service, "userFavoriteCountService", favoriteCountService);

        when(statsMapper.selectById(42L)).thenReturn(null);
        when(memeCatalog.countPublishedByAuthor(42L)).thenReturn(7L);
        when(favoriteCountService.getFavoriteCount(42L)).thenReturn(3);

        UserProfileStatsVO stats = ReflectionTestUtils.invokeMethod(service, "buildStats", 42L);

        assertEquals(7, stats.getMemeCount());
        assertEquals(3, stats.getFavoriteCount());
    }

    @Test
    void publishedMemeCountComesFromMemeCatalogWhenStatsRowExists() {
        UserStatsMapper statsMapper = mock(UserStatsMapper.class);
        MemeCatalogPort memeCatalog = mock(MemeCatalogPort.class);
        UserFavoriteCountService favoriteCountService = mock(UserFavoriteCountService.class);
        UserProfileServiceImpl service = new UserProfileServiceImpl();
        ReflectionTestUtils.setField(service, "userStatsMapper", statsMapper);
        ReflectionTestUtils.setField(service, "memeCatalogPort", memeCatalog);
        ReflectionTestUtils.setField(service, "userFavoriteCountService", favoriteCountService);

        UserStats persistedStats = new UserStats();
        persistedStats.setUserId(42L);
        when(statsMapper.selectById(42L)).thenReturn(persistedStats);
        when(memeCatalog.countPublishedByAuthor(42L)).thenReturn(2L);
        when(favoriteCountService.getFavoriteCount(42L)).thenReturn(0);

        UserProfileStatsVO stats = ReflectionTestUtils.invokeMethod(service, "buildStats", 42L);

        assertEquals(2, stats.getMemeCount());
    }
}
