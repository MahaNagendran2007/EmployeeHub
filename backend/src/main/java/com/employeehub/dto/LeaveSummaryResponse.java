package com.employeehub.dto;

import java.util.Map;

public record LeaveSummaryResponse(long total, Map<String, Long> byStatus, Map<String, Long> byType, long approvedDays) { }
