package com.sakana.just_because_meme_understands_you.modules.user.application.follow;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
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
    @Mock MemeTagMapper tagMapper;
    @Mock UserTagFollowMapper followMapper;
    @InjectMocks TagFollowService service;

    @Test
    void followRejectsMissingTag() {
        assertThrows(BizException.class, () -> service.follow(7L, 99));
        verifyNoInteractions(followMapper);
    }

    @Test
    void followIsIdempotent() {
        when(tagMapper.selectById(4)).thenReturn(new MemeTag().setId(4).setName("热梗"));
        service.follow(7L, 4);
        service.follow(7L, 4);
        verify(followMapper, times(2)).insertIfAbsent(7L, 4);
    }

    @Test
    void listReturnsFollowedTagsInMapperOrder() {
        when(followMapper.selectFollowedTags(7L)).thenReturn(List.of(
                new MemeTag().setId(3).setName("甲").setRelatedQuantity("12"),
                new MemeTag().setId(8).setName("乙").setRelatedQuantity("4")));
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
