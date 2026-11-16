package com.employeehub.dto;

import java.time.Instant;

public record ActivityResponse(String id, Instant timestamp, String action, String entityType, String entityId,
                               String summary, String actor) { }
