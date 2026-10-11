package com.sakana.just_because_meme_understands_you.api;

import com.sakana.just_because_meme_understands_you.api.user.controller.HomeController;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.meme.application.IMemeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagFeedControllerTest {
    @Mock IMemeService memeService;
    @Mock HttpServletRequest request;
    @InjectMocks HomeController controller;

    @Test
    void tagFeedRequiresLogin() {
        BizException error = assertThrows(BizException.class, () -> controller.list(1, "tags", 16, request));
        assertEquals(401, error.getCode());
        verifyNoInteractions(memeService);
    }

    @Test
    void tagFeedRoutesToFollowedTagQuery() {
        when(request.getAttribute(AuthContext.ATTR_USER_ID)).thenReturn(7L);
        PageVO<?> page = new PageVO<>();
        when(memeService.pageFollowedTagFeed(2, 8, 7L)).thenReturn((PageVO) page);
        assertSame(page, controller.list(2, "tags", 8, request).getData());
    }
}
