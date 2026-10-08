package com.sakana.just_because_meme_understands_you.modules.user.application.profile.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.AccountProfileVO;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserProfileViewMapperTest {

    private final UserProfileViewMapper mapper = Mappers.getMapper(UserProfileViewMapper.class);

    @Test
    void mapsSharedProfileFieldsAndKeepsAvatarForOssEnrichment() {
        User source = new User();
        source.setId(42L);
        source.setNickname("  Sakana  ");
        source.setSignature(null);
        source.setGender(null);
        source.setBirthday(null);
        source.setAvatar("avatar/key");

        AccountProfileVO result = mapper.toAccountProfileVO(source);

        assertEquals("42", result.getUserId());
        assertEquals("Sakana", result.getNickname());
        assertEquals("", result.getSignature());
        assertEquals(0, result.getGender());
        assertEquals("", result.getBirthday());
        assertNull(result.getAvatar());
    }
}
