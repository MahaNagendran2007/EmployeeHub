package com.employeehub.service;

import com.employeehub.dto.NotificationResponse;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Notification;
import com.employeehub.repository.NotificationRepository;
import com.employeehub.security.Actor;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** In-app notifications only. No e-mail is sent. */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void notifyRoles(Set<String> roles, String type, String message, String entityId) {
        Notification n = base(type, message, entityId);
        n.setRecipientRoles(roles);
        repository.save(n);
    }

    public void notifyUser(String username, String type, String message, String entityId) {
        Notification n = base(type, message, entityId);
        n.setRecipientUsername(username);
        repository.save(n);
    }

    private Notification base(String type, String message, String entityId) {
        Notification n = new Notification();
        n.setType(type);
        n.setMessage(message);
        n.setEntityId(entityId);
        n.setCreatedAt(clock.instant());
        return n;
    }

    public List<NotificationResponse> list(Actor actor, int size) {
        int safe = Math.min(Math.max(size, 1), 50);
        return repository.findVisible(actor.username(), actor.role().name(),
                        PageRequest.of(0, safe, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream().map(n -> toResponse(n, actor)).toList();
    }

    public long unreadCount(Actor actor) {
        return repository.countUnread(actor.username(), actor.role().name());
    }

    public void markRead(Actor actor, String id) {
        Notification n = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        boolean visible = actor.username().equals(n.getRecipientUsername()) || n.getRecipientRoles().contains(actor.role().name());
        if (!visible) {
            throw new ResourceNotFoundException("Notification not found");
        }
        if (n.getReadBy().add(actor.username())) {
            repository.save(n);
        }
    }

    public void markAllRead(Actor actor) {
        repository.findVisible(actor.username(), actor.role().name(),
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"))).forEach(n -> {
            if (n.getReadBy().add(actor.username())) {
                repository.save(n);
            }
        });
    }

    private NotificationResponse toResponse(Notification n, Actor actor) {
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getEntityId(), n.getCreatedAt(),
                n.getReadBy().contains(actor.username()));
    }
}
