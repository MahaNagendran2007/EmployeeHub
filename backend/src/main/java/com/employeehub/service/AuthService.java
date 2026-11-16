package com.employeehub.service;

import com.employeehub.dto.LoginRequest;
import com.employeehub.dto.LoginResponse;
import com.employeehub.dto.UserSummary;
import com.employeehub.exception.ForbiddenException;
import com.employeehub.model.UserAccount;
import com.employeehub.repository.UserRepository;
import com.employeehub.security.JwtService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    /** Used to spend the same time hashing when the username does not exist (blunts user enumeration by timing). */
    private final String dummyHash;

    private record Attempts(int failures, Instant lockedUntil) { }
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final ActivityService activity;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, ActivityService activity, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.activity = activity;
        this.clock = clock;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase();
        Instant now = clock.instant();
        Attempts current = attempts.get(username);
        if (current != null && current.lockedUntil() != null && current.lockedUntil().isAfter(now)) {
            throw new ForbiddenException("Too many failed attempts. Try again later.");
        }

        Optional<UserAccount> found = users.findByUsername(username).filter(UserAccount::isEnabled);
        boolean ok = encoder.matches(request.password(), found.map(UserAccount::getPasswordHash).orElse(dummyHash)) && found.isPresent();
        if (!ok) {
            int failures = (current == null ? 0 : current.failures()) + 1;
            attempts.put(username, new Attempts(failures, failures >= MAX_FAILURES ? now.plus(LOCK) : null));
            activity.record(username, "LOGIN_FAILED", "USER", username, "Failed login attempt");
            throw new BadCredentialsException("Invalid username or password");
        }
        attempts.remove(username);
        UserAccount user = found.get();
        activity.record(user.getUsername(), "LOGIN_SUCCESS", "USER", user.getId(), "User signed in");
        JwtService.Token token = jwt.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), toSummary(user));
    }

    public static UserSummary toSummary(UserAccount u) {
        return new UserSummary(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getEmployeeCode(),
                u.isEnabled(), u.getCreatedAt());
    }
}
