package com.sakana.just_because_meme_understands_you.modules.user.domain;

import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserDisplayPolicyTest {

    @Test
    void anonymizesThePublicIdentityOfDeletedUsers() {
        User user = new User();
        user.setNickname("原昵称");
        user.setAvatar("avatar-key");
        user.setSignature("原签名");
        user.setDeletedAt(LocalDateTime.now());

        assertEquals("已注销用户", UserDisplayPolicy.displayName(user, "匿名用户"));
        assertNull(UserDisplayPolicy.displayAvatar(user));
        assertEquals("", UserDisplayPolicy.displaySignature(user));
    }

    @Test
    void preservesThePublicIdentityOfActiveUsers() {
        User user = new User();
        user.setNickname("梗友");
        user.setAvatar("avatar-key");
        user.setSignature("签名");

        assertEquals("梗友", UserDisplayPolicy.displayName(user, "匿名用户"));
        assertEquals("avatar-key", UserDisplayPolicy.displayAvatar(user));
        assertEquals("签名", UserDisplayPolicy.displaySignature(user));
    }
}
