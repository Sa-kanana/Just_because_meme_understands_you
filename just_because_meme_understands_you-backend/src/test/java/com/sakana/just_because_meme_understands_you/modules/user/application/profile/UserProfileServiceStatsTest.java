package com.sakana.just_because_meme_understands_you.modules.user.application.profile;

import com.sakana.just_because_meme_understands_you.api.user.dto.UserProfileUpdateRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AccountProfileVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.UserProfileStatsVO;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.user.application.favorite.UserFavoriteCountService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.impl.UserProfileServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.assembler.UserProfileViewMapper;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserStats;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserStatsMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssObjectPromoteService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
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

    @Test
    void profileAvatarUpdateMovesFileReferenceInsideTheUserUpdateFlow() {
        Long userId = 42L;
        User user = new User();
        user.setId(userId);
        user.setAvatar("avatar/42/old.png");
        IUserService userService = mock(IUserService.class);
        when(userService.getById(userId)).thenReturn(user);
        when(userService.updateById(user)).thenReturn(true);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        when(fileAssetService.promote("https://cdn.example/avatar/42/new.png", userId, "avatar"))
                .thenReturn("avatar/42/new.png");
        UserProfileViewMapper viewMapper = mock(UserProfileViewMapper.class);
        when(viewMapper.toAccountProfileVO(user)).thenReturn(new AccountProfileVO());
        OssUrlHelper urlHelper = mock(OssUrlHelper.class);
        when(urlHelper.toPublicUrl("avatar/42/new.png")).thenReturn("https://cdn.example/avatar/42/new.png");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.keys(org.mockito.ArgumentMatchers.anyString())).thenReturn(Collections.emptySet());
        UserProfileServiceImpl service = new UserProfileServiceImpl();
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "userProfileViewMapper", viewMapper);
        ReflectionTestUtils.setField(service, "ossUrlHelper", urlHelper);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        setFieldIfPresent(service, "fileAssetService", fileAssetService);
        OssObjectPromoteService obsoletePromoteService = mock(OssObjectPromoteService.class);
        setFieldIfPresent(service, "ossObjectPromoteService", obsoletePromoteService);

        UserProfileUpdateRequestDTO request = new UserProfileUpdateRequestDTO();
        request.setAvatar("https://cdn.example/avatar/42/new.png");

        service.updateProfile(userId, request);

        var order = inOrder(fileAssetService, userService);
        order.verify(fileAssetService).promote("https://cdn.example/avatar/42/new.png", userId, "avatar");
        order.verify(userService).updateById(user);
        order.verify(fileAssetService).replaceReference(
                "avatar/42/new.png", "USER", userId, "AVATAR", "AVATAR_REPLACED");
    }

    private void setFieldIfPresent(Object target, String fieldName, Object value) {
        if (org.springframework.util.ReflectionUtils.findField(target.getClass(), fieldName) != null) {
            ReflectionTestUtils.setField(target, fieldName, value);
        }
    }
}
