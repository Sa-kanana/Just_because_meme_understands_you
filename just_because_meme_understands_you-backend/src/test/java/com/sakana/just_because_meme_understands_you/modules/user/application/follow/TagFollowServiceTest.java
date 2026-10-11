package com.sakana.just_because_meme_understands_you.modules.user.application.follow;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserTagFollowMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagFollowServiceTest {
    @Mock MemeCatalogPort memeCatalogPort;
    @Mock UserTagFollowMapper followMapper;
    @InjectMocks TagFollowService service;

    @Test
    void followRejectsMissingTag() {
        assertThrows(BizException.class, () -> service.follow(7L, 99));
        verifyNoInteractions(followMapper);
    }

    @Test
    void followIsIdempotent() {
        when(memeCatalogPort.findTagById(4)).thenReturn(
                new MemeCatalogPort.MemeTagSummary(4L, "热梗", 1));
        service.follow(7L, 4);
        service.follow(7L, 4);
        verify(followMapper, times(2)).insertIfAbsent(7L, 4);
    }

    @Test
    void listReturnsFollowedTagsInMapperOrder() {
        when(followMapper.selectFollowedTagIds(7L)).thenReturn(List.of(3L, 8L));
        when(memeCatalogPort.findTagsByIds(List.of(3L, 8L))).thenReturn(List.of(
                new MemeCatalogPort.MemeTagSummary(3L, "甲", 12),
                new MemeCatalogPort.MemeTagSummary(8L, "乙", 4)));
        List<MemeTagVO> tags = service.list(7L);
        assertEquals(List.of(3, 8), tags.stream().map(t -> t.getId()).toList());
        assertEquals(List.of("甲", "乙"), tags.stream().map(t -> t.getName()).toList());
        assertEquals(List.of(12, 4), tags.stream().map(t -> t.getRelatedQuantity()).toList());
    }

    @Test
    void unfollowIsIdempotent() {
        service.unfollow(7L, 4);
        service.unfollow(7L, 4);
        verify(followMapper, times(2)).deleteFollow(7L, 4);
    }
}
