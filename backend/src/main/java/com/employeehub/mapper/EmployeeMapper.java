package com.employeehub.mapper;

import com.employeehub.dto.EmployeeRequest;
import com.employeehub.dto.EmployeeResponse;
import com.employeehub.model.Employee;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Converts between API DTOs and the persistence model, normalising unique fields. */
@Component
public class EmployeeMapper {

    public static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Employee toEntity(EmployeeRequest request) {
        Employee employee = new Employee();
        apply(employee, request);
        return employee;
    }

    /** Copies request values onto an entity; id and timestamps are never touched here. */
    public void apply(Employee employee, EmployeeRequest request) {
        employee.setEmployeeCode(normalizeCode(request.employeeCode()));
        employee.setFullName(request.fullName().trim());
        employee.setEmail(normalizeEmail(request.email()));
        employee.setDepartment(request.department().trim());
        employee.setJobTitle(request.jobTitle().trim());
        employee.setLocation(request.location().trim());
        employee.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        employee.setJoiningDate(request.joiningDate());
        employee.setEmploymentStatus(request.employmentStatus());
        employee.setReportingManager(blankToNull(request.reportingManager()));
        employee.setEmergencyContactName(blankToNull(request.emergencyContactName()));
        employee.setEmergencyContactPhone(blankToNull(request.emergencyContactPhone()));
    }

    public EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(e.getId(), e.getEmployeeCode(), e.getFullName(), e.getEmail(),
                e.getDepartment(), e.getJobTitle(), e.getLocation(), e.getPhone(), e.getJoiningDate(),
                e.getEmploymentStatus(), e.getCreatedAt(), e.getUpdatedAt(), e.getReportingManager(),
                e.getEmergencyContactName(), e.getEmergencyContactPhone());
    }
}
