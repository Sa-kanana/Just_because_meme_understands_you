package com.sakana.just_because_meme_understands_you.modules.user.application.follow;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.user.application.follow.impl.UserFollowServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.user.application.safety.UserSafetyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserFollowSafetyTest {
    @Test
    void rejectsFollowBeforeWritingWhenEitherUserBlockedTheOther() {
        UserSafetyService safety = mock(UserSafetyService.class);
        doThrow(new BizException(403, "屏蔽关系不允许互动"))
                .when(safety).assertInteractionAllowed(3L, 9L);
        UserFollowServiceImpl service = new UserFollowServiceImpl();
        ReflectionTestUtils.setField(service, "userSafetyService", safety);

        assertThrows(BizException.class, () -> service.follow(3L, 9L));
        verify(safety).assertInteractionAllowed(3L, 9L);
    }
}
