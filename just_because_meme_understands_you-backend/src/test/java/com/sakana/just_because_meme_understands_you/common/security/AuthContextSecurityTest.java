package com.sakana.just_because_meme_understands_you.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthContextSecurityTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void establishesSpringAuthoritiesFromCanonicalRolesAndKeepsRequestContext() {
        HttpServletRequest request = new MockHttpServletRequest();

        AuthContext.establish(request, "42", List.of("USER", "SUPER_ADMIN"));

        assertEquals(42L, AuthContext.requireCurrentUserId(request));
        assertEquals(List.of("USER", "SUPER_ADMIN"), AuthContext.currentRoles(request));
        assertEquals("ROLE_USER", AuthContext.currentRole(request));
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN")));
    }

    @Test
    void normalizesLegacyAuthoritiesBeforeCreatingSecurityAuthorities() {
        HttpServletRequest request = new MockHttpServletRequest();

        AuthContext.establish(request, "42", List.of("ROLE_ADMIN"));

        assertEquals(List.of("ADMIN"), AuthContext.currentRoles(request));
        assertEquals("ROLE_ADMIN", AuthContext.currentRole(request));
    }
}
