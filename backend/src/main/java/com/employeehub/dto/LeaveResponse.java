package com.employeehub.dto;

import com.employeehub.model.LeaveStatus;
import com.employeehub.model.LeaveType;
import java.time.Instant;
import java.time.LocalDate;

public record LeaveResponse(String id, String employeeId, String employeeCode, String employeeName, LeaveType type,
                            LocalDate startDate, LocalDate endDate, int days, String reason, LeaveStatus status,
                            String requestedBy, Instant createdAt, String decidedBy, Instant decidedAt,
                            String decisionNote) { }
