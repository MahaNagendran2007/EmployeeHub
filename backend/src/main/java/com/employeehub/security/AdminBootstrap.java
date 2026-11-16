package com.employeehub.security;

import com.employeehub.model.Role;
import com.employeehub.model.UserAccount;
import com.employeehub.repository.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the very first ADMIN from environment variables when the users collection is empty.
 * There is no public registration endpoint and no default password.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String username;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder, Clock clock,
                          @Value("${app.bootstrap.admin-username:}") String username,
                          @Value("${app.bootstrap.admin-password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.username = username == null ? "" : username.trim().toLowerCase();
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            log.warn("No users exist. Set EMPLOYEEHUB_ADMIN_USERNAME and EMPLOYEEHUB_ADMIN_PASSWORD and restart to create the first admin.");
            return;
        }
        if (password.length() < 10) {
            log.error("EMPLOYEEHUB_ADMIN_PASSWORD must be at least 10 characters; no admin was created.");
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(username);
        admin.setDisplayName("Administrator");
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setCreatedAt(clock.instant());
        users.save(admin);
        log.info("Initial admin account '{}' created. Remove EMPLOYEEHUB_ADMIN_PASSWORD from your environment now.", username);
    }
}
