package com.employeehub.controller;

import com.employeehub.dto.CreateUserRequest;
import com.employeehub.dto.UserSummary;
import com.employeehub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "Account administration (ADMIN only)")
public class UserController {

    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @Operation(summary = "List accounts")
    @GetMapping
    public List<UserSummary> list() { return service.list(); }

    @Operation(summary = "Create an account (ADMIN, HR or EMPLOYEE)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary create(@Valid @RequestBody CreateUserRequest request) { return service.create(request); }

    @Operation(summary = "Enable or disable an account", description = "Body: {\"enabled\": true|false}. You cannot disable yourself.")
    @PatchMapping("/{id}/enabled")
    public UserSummary setEnabled(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        return service.setEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
    }
}
