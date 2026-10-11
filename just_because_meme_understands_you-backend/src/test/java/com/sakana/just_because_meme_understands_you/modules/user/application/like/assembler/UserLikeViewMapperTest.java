package com.sakana.just_because_meme_understands_you.modules.user.application.like.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeVO;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserLike;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserLikeViewMapperTest {

    private final UserLikeViewMapper mapper = Mappers.getMapper(UserLikeViewMapper.class);

    @Test
    void mapsLikeFieldsAndLeavesAggregateCountForApplicationLayer() {
        UserLike source = new UserLike();
        source.setId(21L);
        source.setMemeId(33L);

        MemeLikeVO result = mapper.toMemeLikeVO(source, true);

        assertEquals(21L, result.getLikeId());
        assertEquals(33L, result.getMemeId());
        assertTrue(result.getLiked());
        org.junit.jupiter.api.Assertions.assertNull(result.getLikeCount());
    }
}
