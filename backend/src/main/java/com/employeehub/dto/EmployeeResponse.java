package com.employeehub.dto;

import com.employeehub.model.EmploymentStatus;
import java.time.Instant;
import java.time.LocalDate;

public record EmployeeResponse(
        String id,
        String employeeCode,
        String fullName,
        String email,
        String department,
        String jobTitle,
        String location,
        String phone,
        LocalDate joiningDate,
        EmploymentStatus employmentStatus,
        Instant createdAt,
        Instant updatedAt,
        String reportingManager,
        String emergencyContactName,
        String emergencyContactPhone) {
}
