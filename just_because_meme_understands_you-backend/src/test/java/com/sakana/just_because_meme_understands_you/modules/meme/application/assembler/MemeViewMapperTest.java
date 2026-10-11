package com.sakana.just_because_meme_understands_you.modules.meme.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeDetailVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.SimpleMemeVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MemeViewMapperTest {

    private final MemeViewMapper mapper = Mappers.getMapper(MemeViewMapper.class);

    @Test
    void mapsTagIdentityAndNameWhileLeavingRelatedCountForApplicationLayer() {
        MemeTag source = new MemeTag();
        source.setId(12);
        source.setName("搞笑");
        source.setRelatedQuantity(31);

        MemeTagVO result = mapper.toMemeTagVO(source);

        assertEquals(12, result.getId());
        assertEquals("搞笑", result.getName());
        assertNull(result.getRelatedQuantity());
    }

    @Test
    void mapsSimpleMemeFieldsAndLeavesAuthorAndTagsForApplicationLayer() {
        Meme source = sampleMeme();

        SimpleMemeVO result = mapper.toSimpleMemeVOBase(source);

        assertEquals(42, result.getId());
        assertEquals("测试梗", result.getName());
        assertEquals("raw/image.png", result.getImage());
        assertEquals(101, result.getPageViews());
        assertEquals(12, result.getLikes());
        assertEquals(7, result.getComments());
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 30), result.getReleaseTime());
        assertEquals(LocalDateTime.of(2026, 10, 2, 13, 45), result.getUpdateTime());
        assertEquals(2, result.getStatus());
        assertNull(result.getAuthor());
        assertNull(result.getMemeTag());
    }

    @Test
    void mapsMemeDetailBaseFieldsAndLeavesComputedFieldsForApplicationLayer() {
        Meme source = sampleMeme();

        MemeDetailVO result = mapper.toMemeDetailVOBase(source);

        assertEquals(42, result.getId());
        assertEquals("测试梗介绍", result.getIntroduction());
        assertEquals("测试梗", result.getName());
        assertEquals("raw/image.png", result.getImage());
        assertEquals(101, result.getPageViews());
        assertEquals(12, result.getLikes());
        assertEquals(7, result.getComments());
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 30), result.getReleaseTime());
        assertEquals(LocalDateTime.of(2026, 10, 2, 13, 45), result.getUpdateTime());
        assertEquals(2, result.getStatus());
        assertNull(result.getStatusDesc());
        assertNull(result.getAuthor());
        assertNull(result.getOwner());
        assertNull(result.getOwnerPreview());
        assertNull(result.getCommentsEnabled());
        assertNull(result.getViewMode());
        assertNull(result.getFavoriteEnabled());
        assertNull(result.getMemeTag());
        assertNull(result.getLinks());
    }

    private Meme sampleMeme() {
        Meme source = new Meme();
        source.setId(42L);
        source.setIntroduction("测试梗介绍");
        source.setName("测试梗");
        source.setImage("raw/image.png");
        source.setPageViews(101);
        source.setLikes(12);
        source.setComments(7);
        source.setReleaseTime(LocalDateTime.of(2026, 10, 1, 12, 30));
        source.setUpdateTime(LocalDateTime.of(2026, 10, 2, 13, 45));
        source.setStatus(2);
        return source;
    }

}
