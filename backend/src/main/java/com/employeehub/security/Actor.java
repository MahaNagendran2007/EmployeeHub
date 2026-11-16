package com.employeehub.security;

import com.employeehub.model.Role;

/** Who is performing an action: username, role and (for EMPLOYEE accounts) the linked employee code. */
public record Actor(String username, Role role, String employeeCode) {
    public boolean isStaff() { return role == Role.ADMIN || role == Role.HR; }
}
