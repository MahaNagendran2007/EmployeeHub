package com.employeehub.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Audit entry. Never stores passwords, tokens or personal details beyond a short summary. */
@Document(collection = "activity_logs")
public class ActivityLog {
    @Id private String id;
    @Indexed private Instant timestamp;
    private String action;
    private String entityType;
    private String entityId;
    private String summary;
    private String actor;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
}
