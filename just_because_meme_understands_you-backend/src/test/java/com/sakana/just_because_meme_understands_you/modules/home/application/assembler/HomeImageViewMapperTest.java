package com.sakana.just_because_meme_understands_you.modules.home.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.HomeImageVO;
import com.sakana.just_because_meme_understands_you.modules.home.domain.entity.HomeImage;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HomeImageViewMapperTest {

    private final HomeImageViewMapper mapper = Mappers.getMapper(HomeImageViewMapper.class);

    @Test
    void mapsImageFieldsAndLeavesPublicUrlForApplicationLayer() {
        HomeImage source = new HomeImage();
        source.setTitle("首页横幅");
        source.setImgUrl("private/object/key.png");
        source.setTargetType(1);
        source.setTargetValue("42");
        source.setSortOrder(3);
        source.setStatus(1);

        HomeImageVO result = mapper.toHomeImageVO(source);

        assertEquals("首页横幅", result.getTitle());
        assertNull(result.getImgUrl());
        assertEquals(1, result.getTargetType());
        assertEquals("42", result.getTargetValue());
        assertEquals(3, result.getSortOrder());
        assertEquals(1, result.getStatus());
    }
}
