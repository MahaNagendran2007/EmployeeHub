package com.employeehub.dto;

import com.employeehub.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[a-z0-9._-]{3,40}$", message = "Username must be 3-40 characters: lower-case letters, digits, dot, underscore or hyphen")
        String username,
        @NotBlank(message = "Display name is required") @Size(max = 100) String displayName,
        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 100, message = "Password must be 10-100 characters") String password,
        @NotNull(message = "Role is required") Role role,
        @Size(max = 20) String employeeCode) { }
