package com.employeehub.service;

import com.employeehub.dto.CountItem;
import com.employeehub.dto.EmployeeRequest;
import com.employeehub.dto.EmployeeResponse;
import com.employeehub.dto.EmployeeStatsResponse;
import com.employeehub.dto.FieldErrorDetail;
import com.employeehub.dto.PageResponse;
import com.employeehub.dto.TrendPoint;
import com.employeehub.exception.BadRequestException;
import com.employeehub.exception.DuplicateResourceException;
import com.employeehub.exception.InvalidIdException;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.mapper.EmployeeMapper;
import com.employeehub.model.Employee;
import com.employeehub.model.EmploymentStatus;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.EmployeeSearchCriteria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    static final Set<String> SORTABLE_FIELDS = Set.of("fullName", "employeeCode", "email", "department",
            "jobTitle", "location", "joiningDate", "employmentStatus", "createdAt", "updatedAt");

    private final EmployeeRepository repository;
    private final EmployeeMapper mapper;
    private final Clock clock;
    private final int maxPageSize;
    private final ActivityService activity;

    public EmployeeService(EmployeeRepository repository, EmployeeMapper mapper, Clock clock,
                           @Value("${app.pagination.max-page-size:100}") int maxPageSize,
                           ActivityService activity) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
        this.maxPageSize = maxPageSize;
        this.activity = activity;
    }

    public EmployeeResponse create(EmployeeRequest request) {
        Employee employee = mapper.toEntity(request);
        assertUnique(employee.getEmployeeCode(), employee.getEmail(), null);

        Instant now = clock.instant();
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);

        Employee saved = save(employee);
        log.info("Created employee id={}", saved.getId());
        activity.record("EMPLOYEE_CREATED", "EMPLOYEE", saved.getId(), "Employee " + saved.getEmployeeCode() + " created");
        return mapper.toResponse(saved);
    }

    public EmployeeResponse getById(String id) {
        return mapper.toResponse(findOrThrow(id));
    }

    public PageResponse<EmployeeResponse> list(EmployeeSearchCriteria criteria, int page, int size,
                                               String sortBy, Sort.Direction direction) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BadRequestException("Cannot sort by '" + sortBy + "'. Allowed fields: "
                    + String.join(", ", new java.util.TreeSet<>(SORTABLE_FIELDS)));
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), maxPageSize);

        // The "id" tiebreaker keeps page boundaries stable when many rows share the sort value.
        Sort sort = Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "id"));
        Page<Employee> result = repository.search(criteria, PageRequest.of(safePage, safeSize, sort));
        return PageResponse.from(result.map(mapper::toResponse));
    }

    public EmployeeResponse update(String id, EmployeeRequest request) {
        Employee employee = findOrThrow(id);
        mapper.apply(employee, request);
        assertUnique(employee.getEmployeeCode(), employee.getEmail(), employee.getId());

        employee.setUpdatedAt(clock.instant());
        Employee saved = save(employee);
        log.info("Updated employee id={}", saved.getId());
        activity.record("EMPLOYEE_UPDATED", "EMPLOYEE", saved.getId(), "Employee " + saved.getEmployeeCode() + " updated");
        return mapper.toResponse(saved);
    }

    public void delete(String id) {
        validateId(id);
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Employee with id '" + id + "' was not found");
        }
        repository.deleteById(id);
        log.info("Deleted employee id={}", id);
        activity.record("EMPLOYEE_DELETED", "EMPLOYEE", id, "Employee record deleted");
    }

    public EmployeeResponse getByEmployeeCode(String code) {
        return repository.findByEmployeeCode(code)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No employee record is linked to your account"));
    }

    /** Employees that joined per month for the last {@code months} months (zero-filled), from MongoDB aggregation. */
    public List<TrendPoint> joiningTrend(int months) {
        int n = Math.min(Math.max(months, 1), 36);
        YearMonth current = YearMonth.from(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
        YearMonth first = current.minusMonths(n - 1L);
        Map<String, Long> counted = repository.countJoinedByMonth(first.atDay(1), current.plusMonths(1).atDay(1));
        List<TrendPoint> points = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(current); m = m.plusMonths(1)) {
            points.add(new TrendPoint(m.toString(), counted.getOrDefault(m.toString(), 0L)));
        }
        return points;
    }

    public List<String> designations() {
        return repository.distinctValues("jobTitle");
    }

    private static final int EXPORT_LIMIT = 50_000;

    /** CSV of every employee matching the criteria (sorted like the list view). */
    public String exportCsv(EmployeeSearchCriteria criteria, String sortBy, Sort.Direction direction) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BadRequestException("Cannot sort by '" + sortBy + "'");
        }
        Sort sort = Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "id"));
        List<Employee> matches = repository.findAllMatching(criteria, sort, EXPORT_LIMIT);
        activity.record("EMPLOYEES_EXPORTED", "EMPLOYEE", null, matches.size() + " employee record(s) exported to CSV");
        return EmployeeCsv.write(matches);
    }

    public EmployeeStatsResponse stats() {
        Map<String, Long> counted = repository.countGroupedBy("employmentStatus");
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (EmploymentStatus status : EmploymentStatus.values()) {
            byStatus.put(status.name(), counted.getOrDefault(status.name(), 0L));
        }
        return new EmployeeStatsResponse(repository.count(), byStatus, toItems(repository.countGroupedBy("department")));
    }

    public List<CountItem> locations() {
        return toItems(repository.countGroupedBy("location"));
    }

    private List<CountItem> toItems(Map<String, Long> counts) {
        return counts.entrySet().stream().map(e -> new CountItem(e.getKey(), e.getValue())).toList();
    }

    private Employee findOrThrow(String id) {
        validateId(id);
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee with id '" + id + "' was not found"));
    }

    private void validateId(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new InvalidIdException(id);
        }
    }

    /** Friendly pre-check; the unique indexes remain the final guard against concurrent writes. */
    private void assertUnique(String code, String email, String excludeId) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        repository.findByEmployeeCode(code)
                .filter(other -> !Objects.equals(other.getId(), excludeId))
                .ifPresent(other -> errors.add(new FieldErrorDetail("employeeCode",
                        "Employee code '" + code + "' is already in use")));
        repository.findByEmail(email)
                .filter(other -> !Objects.equals(other.getId(), excludeId))
                .ifPresent(other -> errors.add(new FieldErrorDetail("email",
                        "Email '" + email + "' is already in use")));
        if (!errors.isEmpty()) {
            throw new DuplicateResourceException(errors);
        }
    }

    private Employee save(Employee employee) {
        try {
            return repository.save(employee);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateResourceException(List.of(
                    new FieldErrorDetail("employeeCode", "Employee code or email is already in use")));
        }
    }
}
