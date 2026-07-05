/*
 * Function: AuthService unit tests — login success and credential failure paths
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.error.ErrorCode;
import io.opengeobot.platform.common.error.PlatformException;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.iam.domain.RefreshToken;
import io.opengeobot.platform.iam.domain.Session;
import io.opengeobot.platform.iam.domain.User;
import io.opengeobot.platform.iam.dto.LoginRequest;
import io.opengeobot.platform.iam.dto.TokenResponse;
import io.opengeobot.platform.iam.repository.RefreshTokenRepository;
import io.opengeobot.platform.iam.repository.SessionRepository;
import io.opengeobot.platform.iam.repository.UserRepository;
import io.opengeobot.platform.iam.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link AuthService} using Mockito mocks for all
 * collaborators. Covers the login happy path and the two credential failure
 * paths (wrong password and unknown user), both of which must surface the same
 * {@link ErrorCode#AUTH_INVALID_CREDENTIALS} to avoid leaking account existence.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private PublicIdGenerator idGenerator;

    @Mock
    private ClockProvider clockProvider;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuthService authService;

    private User activeUser() {
        User user = new User();
        user.setUserId("usr_01HXYZ");
        user.setUsername("alice");
        user.setPasswordHash("$2a$hashed");
        user.setStatus("ACTIVE");
        return user;
    }

    @Test
    void login_success_returnsTokenResponse() {
        User user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("secret", "$2a$hashed")).thenReturn(true);
        when(userRepository.findPermissionCodesByUserId("usr_01HXYZ"))
                .thenReturn(List.of("iam:user:read"));
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(tokenProvider.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(7));
        when(idGenerator.generate("sess")).thenReturn("sess_01H");
        when(tokenProvider.generateRefreshToken()).thenReturn("rt_abc");
        when(tokenProvider.generateAccessToken(eq("usr_01HXYZ"), eq("alice"), anyList(), eq("sess_01H")))
                .thenReturn("access.jwt");
        when(tokenProvider.getTokenType()).thenReturn("Bearer");
        when(tokenProvider.getAccessTokenExpiry()).thenReturn(Duration.ofMinutes(30));

        TokenResponse response = authService.login(
                new LoginRequest("alice", "secret"), "127.0.0.1", "curl/8");

        assertThat(response.accessToken()).isEqualTo("access.jwt");
        assertThat(response.refreshToken()).isEqualTo("rt_abc");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800L);

        verify(sessionRepository).insert(any(Session.class));
        verify(refreshTokenRepository).insert(any(RefreshToken.class));
        verify(userRepository).updateById(user);
        verify(auditService).record(any(AuditEvent.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void login_invalidCredentials_throwsAndAuditsFailure() {
        User user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());

        PlatformException ex = assertThrows(PlatformException.class, () ->
                authService.login(new LoginRequest("alice", "wrong"), "127.0.0.1", "curl/8"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(auditService).record(any(AuditEvent.class));
        verify(sessionRepository, never()).insert(any(Session.class));
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void login_userNotFound_throwsSameErrorCode() {
        when(userRepository.findByUsername("ghost")).thenReturn(null);
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());

        PlatformException ex = assertThrows(PlatformException.class, () ->
                authService.login(new LoginRequest("ghost", "secret"), "127.0.0.1", "curl/8"));

        // Unknown user must surface the same code as a wrong password to avoid
        // leaking account existence through the authentication API.
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(auditService).record(any(AuditEvent.class));
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
