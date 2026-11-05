package com.winworld.coursestools.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.util.jwt.impl.AuthJwtTokenUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtRequestFilterTest {

    private static final String SECRET = "test-secret-for-jwt-filter-0123456789-abcdefghijklmnopqrstuvwxyz-0123456789";

    private final AuthJwtTokenUtil tokens = new AuthJwtTokenUtil(SECRET);
    private final JwtRequestFilter filter = new JwtRequestFilter(tokens, new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String authorization, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/admin/settings");
        request.setServletPath("/v1/admin/settings");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void garbageTokensAreAnsweredWith401InsteadOfEscapingAsServerErrors() throws Exception {
        for (String header : List.of("Bearer forged.invalid.token", "Bearer ", "Bearer not-a-jwt",
                "Bearer eyJhbGciOiJIUzI1NiJ9.e30")) {
            MockFilterChain chain = new MockFilterChain();

            MockHttpServletResponse response = call(header, chain);

            assertEquals(401, response.getStatus(), header);
            assertTrue(response.getContentAsString().contains("Invalid token"), response.getContentAsString());
            assertNull(chain.getRequest(), "request must not continue for " + header);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    @Test
    void tokenSignedWithAnotherKeyIsStillReportedAsInvalidSignature() throws Exception {
        String foreign = new AuthJwtTokenUtil(SECRET.replace('0', '9'))
                .generateToken("1", Map.of("role", "ADMIN"), Duration.ofMinutes(5));

        MockHttpServletResponse response = call("Bearer " + foreign, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid signature"), response.getContentAsString());
    }

    @Test
    void validTokenAuthenticatesTheRequest() throws Exception {
        String token = tokens.generateToken("42", Map.of("role", "ADMIN"), Duration.ofMinutes(5));
        MockFilterChain chain = new MockFilterChain();

        call("Bearer " + token, chain);

        assertNotNull(chain.getRequest(), "request must continue");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(42, ((UserPrincipal) authentication.getPrincipal()).userId());
        assertEquals("ROLE_ADMIN", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void requestWithoutTokenContinuesAnonymously() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = call(null, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
