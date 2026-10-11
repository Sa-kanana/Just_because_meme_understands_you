package com.sakana.just_because_meme_understands_you.modules.admin.application.assembler;

import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminSensitiveWordVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminTagVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminUserListItemVO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.entity.SensitiveWord;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminViewMapperTest {

    private final AdminViewMapper mapper = Mappers.getMapper(AdminViewMapper.class);

    @Test
    void mapsSensitiveWordFieldsToAdminView() {
        LocalDateTime createTime = LocalDateTime.of(2026, 10, 8, 12, 30);
        LocalDateTime updateTime = LocalDateTime.of(2026, 10, 8, 13, 45);
        SensitiveWord source = new SensitiveWord();
        source.setId(42L);
        source.setWord("测试词");
        source.setCategory("SPAM");
        source.setActionType(2);
        source.setStatus(1);
        source.setCreator("admin");
        source.setCreateTime(createTime);
        source.setUpdateTime(updateTime);

        AdminSensitiveWordVO result = mapper.toSensitiveWordVO(source);

        assertEquals(42L, result.getId());
        assertEquals("测试词", result.getWord());
        assertEquals("SPAM", result.getCategory());
        assertEquals(2, result.getActionType());
        assertEquals(1, result.getStatus());
        assertEquals("admin", result.getCreator());
        assertEquals(createTime, result.getCreateTime());
        assertEquals(updateTime, result.getUpdateTime());
    }

    @Test
    void mapsTagFieldsToAdminView() {
        MemeTag source = new MemeTag();
        source.setId(8);
        source.setName("猫猫");
        source.setRelatedQuantity(23);

        AdminTagVO result = mapper.toTagVO(source);

        assertEquals(8, result.getId());
        assertEquals("猫猫", result.getName());
        assertEquals("23", result.getRelatedQuantity());
    }

    @Test
    void mapsUserFieldsAndLeavesRoleAndAvatarConversionForApplicationLayer() {
        User source = new User();
        source.setId(19L);
        source.setNickname("用户昵称");
        source.setAvatar("private/avatar.png");
        source.setSignature("签名");
        source.setStatus(1);

        AdminUserListItemVO result = mapper.toAdminUserListItemVO(source);

        assertEquals(19L, result.getId());
        assertEquals("用户昵称", result.getNickname());
        assertNull(result.getAvatar());
        assertEquals("签名", result.getSignature());
        assertNull(result.getRole());
        assertEquals(1, result.getStatus());
    }
}
