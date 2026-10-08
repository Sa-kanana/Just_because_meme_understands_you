package com.sakana.just_because_meme_understands_you.modules.user.application.safety;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserSuppression;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserSuppressionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserSafetyServiceTest {
    private UserMapper users;
    private UserSuppressionMapper suppressions;
    private UserSafetyService service;

    @BeforeEach
    void setUp() {
        users = mock(UserMapper.class);
        suppressions = mock(UserSuppressionMapper.class);
        service = new UserSafetyService();
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "suppressionMapper", suppressions);
    }

    @Test
    void rejectsSelfAndInactiveTargets() {
        assertEquals(400, assertThrows(BizException.class, () -> service.apply(7L, 7L, "BLOCK")).getCode());
        when(users.selectById(8L)).thenReturn(user(8L, 0, null));
        assertEquals(404, assertThrows(BizException.class, () -> service.apply(7L, 8L, "MUTE")).getCode());
        verify(suppressions, never()).insert(any(UserSuppression.class));
    }

    @Test
    void addsRelationsIdempotentlyAndKeepsThemScopedToOwner() {
        when(users.selectById(8L)).thenReturn(user(8L, 1, null));
        when(suppressions.countRelation(7L, 8L, "MUTE")).thenReturn(0, 1);
        when(suppressions.countRelation(7L, 8L, "BLOCK")).thenReturn(0);

        service.apply(7L, 8L, "MUTE");
        service.apply(7L, 8L, "MUTE");

        verify(suppressions, times(1)).insert(any(UserSuppression.class));
        UserSuppression relation = new UserSuppression();
        relation.setOwnerUserId(7L);
        relation.setTargetUserId(8L);
        relation.setType("MUTE");
        when(suppressions.selectOwned(7L)).thenReturn(List.of(relation));
        assertEquals(1, service.list(7L).size());
        when(users.selectById(8L)).thenReturn(user(8L, 1, "target"));
        assertEquals(8L, service.list(7L).get(0).getTargetUserId());
    }

    @Test
    void muteHidesOnlyForOwnerAndBlockPreventsInteractionsInEitherDirection() {
        when(suppressions.countRelation(7L, 8L, "MUTE")).thenReturn(1);
        when(suppressions.existsBlockBetween(7L, 8L)).thenReturn(1);
        when(suppressions.existsBlockBetween(8L, 7L)).thenReturn(0);

        assertTrue(service.isHidden(7L, 8L));
        assertFalse(service.isHidden(8L, 7L));
        assertTrue(service.status(7L, 8L).isMuted());
        assertEquals(403, assertThrows(BizException.class,
                () -> service.assertInteractionAllowed(7L, 8L)).getCode());
    }

    @Test
    void removeIsIdempotentAndRejectsUnknownTypes() {
        assertEquals(400, assertThrows(BizException.class, () -> service.apply(7L, 8L, "FOLLOW")).getCode());
        service.remove(7L, 8L, "BLOCK");
        verify(suppressions).deleteOwned(7L, 8L, "BLOCK");
    }

    private static User user(Long id, Integer status, String nickname) {
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        user.setNickname(nickname);
        return user;
    }
}
