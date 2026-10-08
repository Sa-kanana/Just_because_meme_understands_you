package com.sakana.just_because_meme_understands_you.api;

import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.api.user.support.FavoriteFolderIdParser;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;
import com.sakana.just_because_meme_understands_you.modules.auth.domain.AuthConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 拆分工具后保留历史参数语义和管理端授权规则。 */
class RequestSupportTests {

    @Test
    void positiveIdsTrimWhitespaceAndRejectZero() {
        assertEquals(12L, RequestIdParser.parseLongId(" 12 ", "id"));
        assertThrows(BizException.class, () -> RequestIdParser.parseLongId("0", "id"));
        assertThrows(BizException.class, () -> RequestIdParser.parseLongId("invalid", "id"));
    }

    @Test
    void legacyUserIdParsingKeepsSignAndWhitespaceRules() {
        assertEquals(-1L, RequestIdParser.parseLong("-1", "userId"));
        assertEquals(0L, RequestIdParser.parseLong("0", "userId"));
        assertThrows(BizException.class, () -> RequestIdParser.parseLong(" 1 ", "userId"));
    }

    @Test
    void defaultFavoriteFolderKeepsZeroId() {
        assertEquals(0L, FavoriteFolderIdParser.parseFolderId(" 0 "));
        assertThrows(BizException.class, () -> FavoriteFolderIdParser.parseFolderId("-1"));
    }

    @Test
    void adminRoleAndConfiguredAllowListKeepExistingAccessRules() {
        var request = new MockHttpServletRequest();
        request.setAttribute(AuthContext.ATTR_USER_ID, 12L);
        request.setAttribute(AuthContext.ATTR_ROLE, AuthConstants.ROLE_USER);
        assertThrows(BizException.class, () -> AdminAuthSupport.requireAdmin(request));
        assertEquals(12L, AdminAuthSupport.requireAdmin(request, List.of(12L)));
        request.setAttribute(AuthContext.ATTR_ROLE, AuthConstants.ROLE_ADMIN);
        assertEquals(12L, AdminAuthSupport.requireAdmin(request));
        request.removeAttribute(AuthContext.ATTR_USER_ID);
        assertThrows(BizException.class, () -> AdminAuthSupport.requireAdmin(request));
    }
}
