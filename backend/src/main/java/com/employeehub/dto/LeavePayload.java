package com.employeehub.dto;

import com.employeehub.model.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record LeavePayload(
        /** Required for ADMIN/HR filing on behalf of someone; ignored for EMPLOYEE accounts. */
        String employeeId,
        @NotNull(message = "Leave type is required") LeaveType type,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "End date is required") LocalDate endDate,
        @NotBlank(message = "Reason is required")
        @Size(min = 5, max = 500, message = "Reason must be 5-500 characters") String reason) { }
