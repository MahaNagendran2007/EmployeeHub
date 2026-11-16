package com.employeehub.repository;

import com.employeehub.model.EmploymentStatus;

/** Optional search and filter values; blank strings are treated as "not set". */
public record EmployeeSearchCriteria(String search, String department, String location, EmploymentStatus status,
                                     String designation) {

    public EmployeeSearchCriteria {
        search = clean(search);
        department = clean(department);
        location = clean(location);
        designation = clean(designation);
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
