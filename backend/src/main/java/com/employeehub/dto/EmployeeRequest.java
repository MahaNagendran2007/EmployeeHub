package com.employeehub.dto;

import com.employeehub.model.EmploymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Payload used to create or fully update an employee")
public record EmployeeRequest(

        @Schema(example = "EMP-1001", description = "Unique business identifier (stored upper-case)")
        @NotBlank(message = "Employee code is required")
        @Pattern(regexp = "^[A-Za-z0-9-]{2,20}$",
                message = "Employee code must be 2-20 characters: letters, digits or hyphens")
        String employeeCode,

        @Schema(example = "Priya Sharma")
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters")
        String fullName,

        @Schema(example = "priya.sharma@example.com", description = "Unique, stored lower-case")
        @NotBlank(message = "Email is required")
        @Size(max = 254, message = "Email must be at most 254 characters")
        @Email(message = "Email must be a valid address")
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Email must be a valid address")
        String email,

        @Schema(example = "Engineering")
        @NotBlank(message = "Department is required")
        @Size(max = 100, message = "Department must be at most 100 characters")
        String department,

        @Schema(example = "Software Engineer")
        @NotBlank(message = "Job title is required")
        @Size(max = 100, message = "Job title must be at most 100 characters")
        String jobTitle,

        @Schema(example = "Chennai")
        @NotBlank(message = "Location is required")
        @Size(max = 100, message = "Location must be at most 100 characters")
        String location,

        @Schema(example = "+91 98765 43210", description = "Optional")
        @Pattern(regexp = "^$|^[+0-9()\\-\\s]{7,20}$",
                message = "Phone must be 7-20 characters: digits, spaces, +, - or parentheses")
        String phone,

        @Schema(example = "2024-07-01", description = "ISO date (yyyy-MM-dd)")
        @NotNull(message = "Joining date is required")
        LocalDate joiningDate,

        @Schema(example = "ACTIVE")
        @NotNull(message = "Employment status is required")
        EmploymentStatus employmentStatus,

        @Schema(example = "Anita Rao", description = "Optional reporting manager (free text)")
        @Size(max = 100, message = "Reporting manager must be at most 100 characters")
        String reportingManager,

        @Schema(description = "Optional emergency contact name (sensitive: only returned to ADMIN/HR)")
        @Size(max = 100, message = "Emergency contact name must be at most 100 characters")
        String emergencyContactName,

        @Schema(description = "Optional emergency contact phone (sensitive)")
        @Pattern(regexp = "^$|^[+0-9()\\-\\s]{7,20}$",
                message = "Emergency contact phone must be 7-20 characters: digits, spaces, +, - or parentheses")
        String emergencyContactPhone) {
}
