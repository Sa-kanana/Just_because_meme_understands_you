package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.api.user.vo.UserMemePageVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerSearchTest {

    @Mock
    private IUserProfileService userProfileService;

    @InjectMocks
    private UserProfileController controller;

    @Test
    void forwardsKeywordAndPaginationToProfileMemeService() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        UserMemePageVO page = new UserMemePageVO();
        when(userProfileService.pageUserMemes(42L, null, 1, 12, null, "latest", "柴犬"))
                .thenReturn(page);

        Result<UserMemePageVO> result = controller.pageUserMemes(
                "42", request, 1, 12, null, "latest", "柴犬");

        assertEquals(Result.CODE_SUCCESS, result.getCode());
        assertEquals(page, result.getData());
        verify(userProfileService).pageUserMemes(42L, null, 1, 12, null, "latest", "柴犬");
    }
}
