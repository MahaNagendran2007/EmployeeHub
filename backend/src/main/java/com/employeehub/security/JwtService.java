package com.employeehub.security;

import com.employeehub.model.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public record Token(String value, Instant expiresAt) { }

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, Clock clock, @Value("${app.security.jwt-ttl-minutes:60}") long ttlMinutes) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Token issue(UserAccount user) {
        Instant now = clock.instant();
        Instant expires = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("employeehub")
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expires)
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Token(value, expires);
    }
}
