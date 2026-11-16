package com.employeehub.service;

import com.employeehub.dto.EmployeeRequest;
import com.employeehub.dto.ImportResult;
import com.employeehub.dto.ImportResult.RowError;
import com.employeehub.exception.BadRequestException;
import com.employeehub.exception.DuplicateResourceException;
import com.employeehub.mapper.EmployeeMapper;
import com.employeehub.model.EmploymentStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Imports employees from CSV. Required columns: employeeCode, fullName, email, department, jobTitle, location, joiningDate.
 * Optional: phone, employmentStatus (defaults to ACTIVE), reportingManager, emergencyContactName, emergencyContactPhone.
 * Valid rows are saved even when other rows fail; every failure is reported with its row number.
 */
@Service
public class EmployeeImportService {

    private static final int MAX_REPORTED_ERRORS = 100;
    private static final List<String> REQUIRED = List.of("employeeCode", "fullName", "email", "department", "jobTitle", "location", "joiningDate");

    private final EmployeeService employees;
    private final Validator validator;
    private final ActivityService activity;
    private final int maxRows;

    public EmployeeImportService(EmployeeService employees, Validator validator, ActivityService activity,
                                 @Value("${app.import.max-rows:1000}") int maxRows) {
        this.employees = employees;
        this.validator = validator;
        this.activity = activity;
        this.maxRows = maxRows;
    }

    public ImportResult importCsv(String csv) {
        List<List<String>> rows = EmployeeCsv.parse(csv);
        if (rows.isEmpty()) {
            throw new BadRequestException("The file is empty");
        }
        Map<String, Integer> col = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = REQUIRED.stream().filter(h -> !col.containsKey(h.toLowerCase(Locale.ROOT))).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing required column(s): " + String.join(", ", missing));
        }
        int dataRows = rows.size() - 1;
        if (dataRows > maxRows) {
            throw new BadRequestException("The file has " + dataRows + " rows; the limit is " + maxRows);
        }

        List<RowError> errors = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        int imported = 0;

        for (int r = 1; r < rows.size(); r++) {
            int rowNumber = r + 1;                       // 1-based, counting the header as row 1
            List<String> cells = rows.get(r);
            try {
                EmployeeRequest request = toRequest(cells, col);
                String problems = validator.validate(request).stream()
                        .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; "));
                if (!problems.isEmpty()) {
                    errors.add(new RowError(rowNumber, problems));
                    continue;
                }
                String code = EmployeeMapper.normalizeCode(request.employeeCode());
                String email = EmployeeMapper.normalizeEmail(request.email());
                if (!seenCodes.add(code) || !seenEmails.add(email)) {
                    errors.add(new RowError(rowNumber, "Duplicate employee code or email inside this file"));
                    continue;
                }
                employees.create(request);
                imported++;
            } catch (DuplicateResourceException ex) {
                errors.add(new RowError(rowNumber, ex.getFieldErrors().stream()
                        .map(f -> f.message()).collect(Collectors.joining("; "))));
            } catch (IllegalArgumentException ex) {
                errors.add(new RowError(rowNumber, ex.getMessage()));
            }
        }
        int skipped = dataRows - imported;
        activity.record("EMPLOYEES_IMPORTED", "EMPLOYEE", null, imported + " imported, " + skipped + " skipped from CSV");
        boolean truncated = errors.size() > MAX_REPORTED_ERRORS;
        return new ImportResult(dataRows, imported, skipped,
                truncated ? errors.subList(0, MAX_REPORTED_ERRORS) : errors, truncated);
    }

    private EmployeeRequest toRequest(List<String> cells, Map<String, Integer> col) {
        LocalDate date;
        String rawDate = get(cells, col, "joiningDate");
        try {
            date = rawDate.isEmpty() ? null : LocalDate.parse(rawDate);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("joiningDate must be yyyy-MM-dd");
        }
        String rawStatus = get(cells, col, "employmentStatus");
        EmploymentStatus status;
        try {
            status = rawStatus.isEmpty() ? EmploymentStatus.ACTIVE
                    : EmploymentStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("employmentStatus must be ACTIVE, ON_LEAVE or INACTIVE");
        }
        return new EmployeeRequest(get(cells, col, "employeeCode"), get(cells, col, "fullName"), get(cells, col, "email"),
                get(cells, col, "department"), get(cells, col, "jobTitle"), get(cells, col, "location"),
                get(cells, col, "phone"), date, status, get(cells, col, "reportingManager"),
                get(cells, col, "emergencyContactName"), get(cells, col, "emergencyContactPhone"));
    }

    private static String get(List<String> cells, Map<String, Integer> col, String name) {
        Integer i = col.get(name.toLowerCase(Locale.ROOT));
        return i == null || i >= cells.size() ? "" : cells.get(i);
    }
}
