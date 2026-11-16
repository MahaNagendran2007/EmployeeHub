package com.employeehub.repository;

import com.employeehub.model.Employee;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Custom query operations that the derived-query repository methods cannot express. */
public interface EmployeeSearchRepository {

    Page<Employee> search(EmployeeSearchCriteria criteria, Pageable pageable);

    /** Counts employees grouped by the given field, using a MongoDB aggregation (no in-memory grouping). */
    Map<String, Long> countGroupedBy(String field);

    /** All employees matching the criteria (used for export), capped at {@code limit}. */
    List<Employee> findAllMatching(EmployeeSearchCriteria criteria, Sort sort, int limit);

    /** Number of employees whose joiningDate is in [from, toExclusive), keyed by "yyyy-MM". Documents without a joiningDate are ignored. */
    Map<String, Long> countJoinedByMonth(LocalDate from, LocalDate toExclusive);

    /** Distinct non-null values of a string field (e.g. jobTitle), sorted. */
    List<String> distinctValues(String field);
}
