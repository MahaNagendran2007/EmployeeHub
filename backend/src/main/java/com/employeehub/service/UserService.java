package com.employeehub.service;

import com.employeehub.dto.CreateUserRequest;
import com.employeehub.dto.FieldErrorDetail;
import com.employeehub.dto.UserSummary;
import com.employeehub.exception.BadRequestException;
import com.employeehub.exception.DuplicateResourceException;
import com.employeehub.exception.InvalidIdException;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Role;
import com.employeehub.model.UserAccount;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.UserRepository;
import com.employeehub.security.CurrentUser;
import java.time.Clock;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;
    private final EmployeeRepository employees;
    private final PasswordEncoder encoder;
    private final ActivityService activity;
    private final Clock clock;

    public UserService(UserRepository users, EmployeeRepository employees, PasswordEncoder encoder,
                       ActivityService activity, Clock clock) {
        this.users = users;
        this.employees = employees;
        this.encoder = encoder;
        this.activity = activity;
        this.clock = clock;
    }

    public List<UserSummary> list() {
        return users.findAll().stream().map(AuthService::toSummary).toList();
    }

    public UserSummary create(CreateUserRequest r) {
        String username = r.username().trim().toLowerCase();
        String code = r.employeeCode() == null || r.employeeCode().isBlank() ? null : r.employeeCode().trim().toUpperCase();
        if (r.role() == Role.EMPLOYEE && code == null) {
            throw new BadRequestException("EMPLOYEE accounts must be linked to an employee code");
        }
        if (code != null && employees.findByEmployeeCode(code).isEmpty()) {
            throw new BadRequestException("No employee exists with code '" + code + "'");
        }
        if (users.existsByUsername(username)) {
            throw new DuplicateResourceException(List.of(new FieldErrorDetail("username", "Username is already taken")));
        }
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setDisplayName(r.displayName().trim());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(r.role());
        u.setEmployeeCode(code);
        u.setEnabled(true);
        u.setCreatedAt(clock.instant());
        try {
            u = users.save(u);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateResourceException(List.of(new FieldErrorDetail("username", "Username is already taken")));
        }
        activity.record("USER_CREATED", "USER", u.getId(), "Account '" + username + "' created with role " + r.role());
        return AuthService.toSummary(u);
    }

    public UserSummary setEnabled(String id, boolean enabled) {
        if (!ObjectId.isValid(id)) {
            throw new InvalidIdException(id);
        }
        UserAccount u = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!enabled && u.getUsername().equals(CurrentUser.username())) {
            throw new BadRequestException("You cannot disable your own account");
        }
        u.setEnabled(enabled);
        users.save(u);
        activity.record(enabled ? "USER_ENABLED" : "USER_DISABLED", "USER", id, "Account '" + u.getUsername() + "'");
        return AuthService.toSummary(u);
    }
}
