package com.employeehub.dto;

import java.time.Instant;

public record NotificationResponse(String id, String type, String message, String entityId, Instant createdAt, boolean read) { }
