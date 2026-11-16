package com.employeehub.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the authenticated caller from the Spring Security context. */
public final class CurrentUser {

    private CurrentUser() { }

    private static Authentication auth() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken) ? a : null;
    }

    /** Username of the caller, or "system" when there is no authenticated user (seeders, tests). */
    public static String username() {
        Authentication a = auth();
        return a == null ? "system" : a.getName();
    }

    public static boolean isAuthenticated() { return auth() != null; }
}
