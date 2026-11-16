package com.employeehub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.employeehub.dto.EmployeeRequest;
import com.employeehub.dto.EmployeeResponse;
import com.employeehub.dto.EmployeeStatsResponse;
import com.employeehub.dto.PageResponse;
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
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    private static final String ID = "665f1c2e8f1b2a3c4d5e6f70";
    private static final String OTHER_ID = "665f1c2e8f1b2a3c4d5e6f71";
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private EmployeeRepository repository;

    @Mock
    private ActivityService activity;

    private EmployeeService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeService(repository, new EmployeeMapper(), Clock.fixed(NOW, ZoneOffset.UTC), 100, activity);
    }

    private static EmployeeRequest request(String code, String email) {
        return new EmployeeRequest(code, "  Priya Sharma ", email, "Engineering", "Engineer", "Chennai",
                "", LocalDate.of(2024, 7, 1), EmploymentStatus.ACTIVE, null, null, null);
    }

    private static Employee employee(String id, String code, String email) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode(code);
        e.setEmail(email);
        e.setFullName("Existing Person");
        e.setDepartment("Sales");
        e.setJobTitle("Rep");
        e.setLocation("Delhi");
        e.setJoiningDate(LocalDate.of(2020, 1, 1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        e.setUpdatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        return e;
    }

    @Test
    void create_normalizesFieldsAndSetsTimestamps() {
        when(repository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(ID);
            return e;
        });

        EmployeeResponse response = service.create(request(" emp-001 ", "  Priya.Sharma@Example.COM "));

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(repository).save(captor.capture());
        Employee saved = captor.getValue();
        assertThat(saved.getEmployeeCode()).isEqualTo("EMP-001");
        assertThat(saved.getEmail()).isEqualTo("priya.sharma@example.com");
        assertThat(saved.getFullName()).isEqualTo("Priya Sharma");
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(response.id()).isEqualTo(ID);
    }

    @Test
    void create_duplicateEmail_isRejectedBeforeSaving() {
        when(repository.findByEmail("a@example.com")).thenReturn(Optional.of(employee(OTHER_ID, "EMP-999", "a@example.com")));

        assertThatThrownBy(() -> service.create(request("EMP-001", "a@example.com")))
                .isInstanceOfSatisfying(DuplicateResourceException.class, ex ->
                        assertThat(ex.getFieldErrors()).extracting("field").containsExactly("email"));
        verify(repository, never()).save(any());
    }

    @Test
    void create_duplicateCodeAndEmail_reportsBothFields() {
        when(repository.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee(OTHER_ID, "EMP-001", "x@example.com")));
        when(repository.findByEmail("a@example.com")).thenReturn(Optional.of(employee(OTHER_ID, "EMP-001", "a@example.com")));

        assertThatThrownBy(() -> service.create(request("EMP-001", "a@example.com")))
                .isInstanceOfSatisfying(DuplicateResourceException.class, ex ->
                        assertThat(ex.getFieldErrors()).extracting("field").containsExactly("employeeCode", "email"));
    }

    @Test
    void create_databaseDuplicateKey_isTranslatedToConflict() {
        when(repository.save(any(Employee.class))).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        assertThatThrownBy(() -> service.create(request("EMP-001", "a@example.com")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void getById_returnsEmployee() {
        when(repository.findById(ID)).thenReturn(Optional.of(employee(ID, "EMP-001", "a@example.com")));

        assertThat(service.getById(ID).employeeCode()).isEqualTo("EMP-001");
    }

    @Test
    void getById_missing_throwsNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_malformedId_throwsInvalidIdWithoutTouchingDatabase() {
        assertThatThrownBy(() -> service.getById("not-an-object-id")).isInstanceOf(InvalidIdException.class);
        verify(repository, never()).findById(any());
    }

    @Test
    void update_changesFieldsKeepsCreatedAtAndRefreshesUpdatedAt() {
        Employee existing = employee(ID, "EMP-001", "old@example.com");
        Instant originalCreatedAt = existing.getCreatedAt();
        when(repository.findById(ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeResponse response = service.update(ID, request("EMP-001", "new@example.com"));

        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.fullName()).isEqualTo("Priya Sharma");
        assertThat(response.createdAt()).isEqualTo(originalCreatedAt);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void update_keepingOwnEmail_isNotADuplicate() {
        Employee existing = employee(ID, "EMP-001", "same@example.com");
        when(repository.findById(ID)).thenReturn(Optional.of(existing));
        when(repository.findByEmail("same@example.com")).thenReturn(Optional.of(existing));
        when(repository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(ID, request("EMP-001", "same@example.com")).id()).isEqualTo(ID);
    }

    @Test
    void update_emailOwnedByAnotherEmployee_throwsDuplicate() {
        when(repository.findById(ID)).thenReturn(Optional.of(employee(ID, "EMP-001", "mine@example.com")));
        when(repository.findByEmail("taken@example.com"))
                .thenReturn(Optional.of(employee(OTHER_ID, "EMP-002", "taken@example.com")));

        assertThatThrownBy(() -> service.update(ID, request("EMP-001", "taken@example.com")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_missing_throwsNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ID, request("EMP-001", "a@example.com")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_existing_removesIt() {
        when(repository.existsById(ID)).thenReturn(true);

        service.delete(ID);

        verify(repository).deleteById(ID);
    }

    @Test
    void delete_missing_throwsNotFoundAndDeletesNothing() {
        when(repository.existsById(ID)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void list_clampsPageSizeAndNegativePageAndBuildsSort() {
        when(repository.search(any(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(employee(ID, "EMP-001", "a@example.com")),
                        inv.getArgument(1), 1));
        EmployeeSearchCriteria criteria = new EmployeeSearchCriteria(" ali ", "", null, EmploymentStatus.ACTIVE, " Engineer ");

        PageResponse<EmployeeResponse> page = service.list(criteria, -5, 1000, "fullName", Sort.Direction.ASC);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<EmployeeSearchCriteria> crit = ArgumentCaptor.forClass(EmployeeSearchCriteria.class);
        verify(repository).search(crit.capture(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("fullName").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(crit.getValue().search()).isEqualTo("ali");
        assertThat(crit.getValue().department()).isNull();
        assertThat(crit.getValue().designation()).isEqualTo("Engineer");
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
    }

    @Test
    void list_unknownSortField_isRejected() {
        EmployeeSearchCriteria none = new EmployeeSearchCriteria(null, null, null, null, null);

        assertThatThrownBy(() -> service.list(none, 0, 10, "password", Sort.Direction.ASC))
                .isInstanceOf(BadRequestException.class);
        verify(repository, never()).search(any(), any());
    }

    @Test
    void stats_fillsMissingStatusesWithZero() {
        Map<String, Long> status = new LinkedHashMap<>();
        status.put("ACTIVE", 7L);
        Map<String, Long> departments = new LinkedHashMap<>();
        departments.put("Engineering", 5L);
        departments.put("Sales", 2L);
        when(repository.countGroupedBy("employmentStatus")).thenReturn(status);
        when(repository.countGroupedBy("department")).thenReturn(departments);
        when(repository.count()).thenReturn(7L);

        EmployeeStatsResponse stats = service.stats();

        assertThat(stats.totalEmployees()).isEqualTo(7);
        assertThat(stats.byStatus()).containsEntry("ACTIVE", 7L).containsEntry("ON_LEAVE", 0L)
                .containsEntry("INACTIVE", 0L);
        assertThat(stats.byDepartment()).extracting("name").containsExactly("Engineering", "Sales");
    }

    @Test
    void locations_mapsCountsToItems() {
        Map<String, Long> locations = new LinkedHashMap<>();
        locations.put("Chennai", 4L);
        when(repository.countGroupedBy("location")).thenReturn(locations);

        assertThat(service.locations()).singleElement()
                .satisfies(item -> {
                    assertThat(item.name()).isEqualTo("Chennai");
                    assertThat(item.count()).isEqualTo(4);
                });
    }

    @Test
    void create_recordsAnActivityEntry() {
        when(repository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(ID);
            return e;
        });

        service.create(request("EMP-001", "a@example.com"));

        verify(activity).record(org.mockito.ArgumentMatchers.eq("EMPLOYEE_CREATED"), org.mockito.ArgumentMatchers.eq("EMPLOYEE"),
                org.mockito.ArgumentMatchers.eq(ID), any());
    }

    @Test
    void joiningTrend_fillsMonthsWithZeroAndOrdersOldestFirst() {
        Map<String, Long> counted = new LinkedHashMap<>();
        counted.put("2025-12", 3L);
        counted.put("2026-01", 2L);
        when(repository.countJoinedByMonth(any(), any())).thenReturn(counted);

        var points = service.joiningTrend(3);

        assertThat(points).extracting("month").containsExactly("2025-11", "2025-12", "2026-01");
        assertThat(points).extracting("count").containsExactly(0L, 3L, 2L);
    }

    @Test
    void joiningTrend_clampsMonthsRange() {
        when(repository.countJoinedByMonth(any(), any())).thenReturn(Map.of());

        assertThat(service.joiningTrend(500)).hasSize(36);
        assertThat(service.joiningTrend(0)).hasSize(1);
    }

    @Test
    void exportCsv_includesHeaderAndRowsAndProtectsFormulas() {
        Employee e = employee(ID, "EMP-001", "a@example.com");
        e.setFullName("=cmd()");
        when(repository.findAllMatching(any(), any(Sort.class), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(e));

        String csv = service.exportCsv(new EmployeeSearchCriteria(null, null, null, null, null), "fullName", Sort.Direction.ASC);

        assertThat(csv).startsWith("employeeCode,fullName,email");
        assertThat(csv).contains("'=cmd()");
    }
}
