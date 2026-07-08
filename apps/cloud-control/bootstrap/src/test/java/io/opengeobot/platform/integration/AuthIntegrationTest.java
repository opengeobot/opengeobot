/*
 * Function: Auth integration tests — login, refresh, logout, profile flow
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.platform.common.error.ErrorCode;
import io.opengeobot.platform.common.error.PlatformException;
import io.opengeobot.platform.iam.dto.TokenResponse;
import io.opengeobot.platform.iam.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the authentication flow. Tests login, refresh, and
 * logout endpoints using MockMvc with a mocked {@link AuthService}.
 *
 * <p>Uses @SpringBootTest with the test profile (H2 in-memory database,
 * Flyway disabled, NATS disabled). The AuthService is mocked so no real
 * database access is required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AuthService authService;

    @Test
    void login_validCredentialsReturnsToken() throws Exception {
        TokenResponse token = new TokenResponse("access-token-123", "refresh-token-456", "Bearer", 1800);
        when(authService.login(any(), any(), any())).thenReturn(token);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"Admin@123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token-123"))
                .andExpect(jsonPath("$.refresh_token").value("refresh-token-456"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(1800));
    }

    @Test
    void login_invalidCredentialsReturns401() throws Exception {
        when(authService.login(any(), any(), any()))
                .thenThrow(new PlatformException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_blankUsernameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"Admin@123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_validTokenReturnsNewTokens() throws Exception {
        TokenResponse token = new TokenResponse("new-access", "new-refresh", "Bearer", 1800);
        when(authService.refresh(any())).thenReturn(token);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token":"refresh-token-456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("new-access"));
    }

    @Test
    @WithMockUser(authorities = {"iam.user.read"})
    void logout_authenticatedReturns204() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token":"refresh-token-456"}
                                """))
                .andExpect(status().isNoContent());

        verify(authService).logout("refresh-token-456");
    }

    @Test
    void logout_unauthenticatedReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token":"refresh-token-456"}
                                """))
                .andExpect(status().isForbidden());
    }
}
