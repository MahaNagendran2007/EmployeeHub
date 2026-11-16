package com.employeehub.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** In-app notification addressed to a specific user and/or to every user holding a role. */
@Document(collection = "notifications")
public class Notification {
    @Id private String id;
    @Indexed private String recipientUsername;
    @Indexed private Set<String> recipientRoles = new HashSet<>();
    private String type;
    private String message;
    private String entityId;
    @Indexed private Instant createdAt;
    private Set<String> readBy = new HashSet<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String v) { this.recipientUsername = v; }
    public Set<String> getRecipientRoles() { return recipientRoles; }
    public void setRecipientRoles(Set<String> v) { this.recipientRoles = v; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Set<String> getReadBy() { return readBy; }
    public void setReadBy(Set<String> readBy) { this.readBy = readBy; }
}
