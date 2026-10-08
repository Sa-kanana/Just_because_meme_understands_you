package com.sakana.just_because_meme_understands_you.modules.notification.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.NotificationItemVO;
import com.sakana.just_because_meme_understands_you.modules.notification.domain.entity.UserNotification;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationViewMapperTest {

    private final NotificationViewMapper mapper = Mappers.getMapper(NotificationViewMapper.class);

    @Test
    void mapsStoredFieldsAndPreservesNotificationPresentationRules() {
        UserNotification source = new UserNotification();
        source.setId(8L);
        source.setType("comment");
        source.setTitle("收到评论");
        source.setContent("有人评论了你的梗");
        source.setIsRead(1);
        source.setCreateTime(LocalDateTime.of(2026, 10, 8, 9, 5, 3));
        source.setTargetType("meme");
        source.setTargetId(25L);
        source.setRefId(31L);

        NotificationItemVO result = mapper.toItemVO(source);

        assertEquals(8L, result.getId());
        assertEquals("comment", result.getType());
        assertTrue(result.getIsRead());
        assertEquals("2026-10-08 09:05:03", result.getCreateTime());
        assertEquals("meme", result.getTargetType());
        assertEquals(25L, result.getTargetId());
        assertEquals(31L, result.getRefId());
        assertNull(result.getActor());
        assertNull(result.getJump());
        assertNull(result.getExtra());
    }
}
