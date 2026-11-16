package com.employeehub.dto;

import java.util.List;
import java.util.Map;

public record EmployeeStatsResponse(
        long totalEmployees,
        Map<String, Long> byStatus,
        List<CountItem> byDepartment) {
}
