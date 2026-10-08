package com.sakana.just_because_meme_understands_you.modules.meme.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MemeViewMapperTest {

    private final MemeViewMapper mapper = Mappers.getMapper(MemeViewMapper.class);

    @Test
    void mapsTagIdentityAndNameWhileLeavingRelatedCountForApplicationLayer() {
        MemeTag source = new MemeTag();
        source.setId(12);
        source.setName("搞笑");
        source.setRelatedQuantity("31");

        MemeTagVO result = mapper.toMemeTagVO(source);

        assertEquals(12, result.getId());
        assertEquals("搞笑", result.getName());
        assertNull(result.getRelatedQuantity());
    }
}
