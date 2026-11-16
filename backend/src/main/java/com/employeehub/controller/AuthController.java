package com.employeehub.controller;

import com.employeehub.dto.LoginRequest;
import com.employeehub.dto.LoginResponse;
import com.employeehub.dto.UserSummary;
import com.employeehub.security.Actor;
import com.employeehub.security.ActorResolver;
import com.employeehub.repository.UserRepository;
import com.employeehub.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService auth;
    private final ActorResolver actors;
    private final UserRepository users;

    public AuthController(AuthService auth, ActorResolver actors, UserRepository users) {
        this.auth = auth;
        this.actors = actors;
        this.users = users;
    }

    @Operation(summary = "Sign in", description = "Returns a short-lived Bearer token. 401 for bad credentials, 403 when temporarily locked.")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @Operation(summary = "Current user")
    @GetMapping("/me")
    public UserSummary me() {
        Actor actor = actors.current();
        return AuthService.toSummary(users.findByUsername(actor.username()).orElseThrow());
    }
}
