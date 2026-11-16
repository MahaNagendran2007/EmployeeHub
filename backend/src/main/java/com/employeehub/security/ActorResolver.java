package com.employeehub.security;

import com.employeehub.exception.ForbiddenException;
import com.employeehub.model.UserAccount;
import com.employeehub.repository.UserRepository;
import org.springframework.stereotype.Component;

/** Turns the authenticated principal into an {@link Actor} using the stored account (source of truth for role). */
@Component
public class ActorResolver {

    private final UserRepository users;

    public ActorResolver(UserRepository users) { this.users = users; }

    public Actor current() {
        UserAccount u = users.findByUsername(CurrentUser.username())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ForbiddenException("Account not found or disabled"));
        return new Actor(u.getUsername(), u.getRole(), u.getEmployeeCode());
    }
}
