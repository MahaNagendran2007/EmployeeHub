package com.employeehub.controller;

import com.employeehub.dto.NotificationResponse;
import com.employeehub.security.ActorResolver;
import com.employeehub.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notifications for the signed-in user")
public class NotificationController {

    private final NotificationService service;
    private final ActorResolver actors;

    public NotificationController(NotificationService service, ActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @Operation(summary = "My notifications, newest first (max 50)")
    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "20") int size) {
        return service.list(actors.current(), size);
    }

    @Operation(summary = "Number of unread notifications")
    @GetMapping("/unread-count")
    public Map<String, Long> unread() { return Map.of("count", service.unreadCount(actors.current())); }

    @Operation(summary = "Mark one notification as read")
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable String id) { service.markRead(actors.current(), id); }

    @Operation(summary = "Mark all of my notifications as read")
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll() { service.markAllRead(actors.current()); }
}
