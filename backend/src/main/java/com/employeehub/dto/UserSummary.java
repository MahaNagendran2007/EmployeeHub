package com.employeehub.dto;

import com.employeehub.model.Role;
import java.time.Instant;

public record UserSummary(String id, String username, String displayName, Role role, String employeeCode,
                          boolean enabled, Instant createdAt) { }
