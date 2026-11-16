package com.employeehub.dto;

import jakarta.validation.constraints.Size;

public record LeaveDecision(@Size(max = 300, message = "Note must be at most 300 characters") String note) { }
