package com.employeehub.dto;

import java.time.Instant;

public record LoginResponse(String token, String tokenType, Instant expiresAt, UserSummary user) { }
