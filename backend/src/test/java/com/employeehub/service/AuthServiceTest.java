package com.employeehub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.employeehub.dto.LoginRequest;
import com.employeehub.dto.LoginResponse;
import com.employeehub.exception.ForbiddenException;
import com.employeehub.model.Role;
import com.employeehub.model.UserAccount;
import com.employeehub.repository.UserRepository;
import com.employeehub.security.JwtService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-10T09:00:00Z");

    @Mock private UserRepository users;
    @Mock private JwtService jwt;
    @Mock private ActivityService activity;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthService service;
    private UserAccount hr;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, jwt, activity, Clock.fixed(NOW, ZoneOffset.UTC));
        hr = new UserAccount();
        hr.setId("665f1c2e8f1b2a3c4d5e6f01");
        hr.setUsername("hr1");
        hr.setDisplayName("HR One");
        hr.setRole(Role.HR);
        hr.setEnabled(true);
        hr.setPasswordHash(encoder.encode("correct-horse-1"));
    }

    @Test
    void login_withCorrectPassword_returnsTokenAndUserWithoutPasswordHash() {
        when(users.findByUsername("hr1")).thenReturn(Optional.of(hr));
        when(jwt.issue(hr)).thenReturn(new JwtService.Token("jwt-value", NOW.plusSeconds(3600)));

        LoginResponse response = service.login(new LoginRequest(" HR1 ", "correct-horse-1"));

        assertThat(response.token()).isEqualTo("jwt-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().role()).isEqualTo(Role.HR);
        assertThat(response.user().toString()).doesNotContain("$2");
        verify(activity).record(eq("hr1"), eq("LOGIN_SUCCESS"), any(), any(), any());
    }

    @Test
    void login_withWrongPassword_isRejectedAndLogged() {
        when(users.findByUsername("hr1")).thenReturn(Optional.of(hr));

        assertThatThrownBy(() -> service.login(new LoginRequest("hr1", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verify(activity).record(eq("hr1"), eq("LOGIN_FAILED"), any(), any(), any());
    }

    @Test
    void login_unknownUser_getsTheSameGenericError() {
        when(users.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody", "whatever")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid username or password");
    }

    @Test
    void login_disabledAccountCannotSignIn() {
        hr.setEnabled(false);
        when(users.findByUsername("hr1")).thenReturn(Optional.of(hr));

        assertThatThrownBy(() -> service.login(new LoginRequest("hr1", "correct-horse-1")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_isLockedAfterFiveFailures_evenWithTheRightPassword() {
        when(users.findByUsername("hr1")).thenReturn(Optional.of(hr));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("hr1", "bad"))).isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> service.login(new LoginRequest("hr1", "correct-horse-1")))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("Too many");
    }
}
