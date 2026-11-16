package com.employeehub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.employeehub.dto.LeavePayload;
import com.employeehub.dto.LeaveResponse;
import com.employeehub.exception.BadRequestException;
import com.employeehub.exception.ConflictException;
import com.employeehub.exception.DuplicateResourceException;
import com.employeehub.exception.ForbiddenException;
import com.employeehub.model.Employee;
import com.employeehub.model.LeaveRequest;
import com.employeehub.model.LeaveStatus;
import com.employeehub.model.LeaveType;
import com.employeehub.model.Role;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.LeaveRepository;
import com.employeehub.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    private static final String EMP_ID = "665f1c2e8f1b2a3c4d5e6f70";
    private static final String OTHER_EMP_ID = "665f1c2e8f1b2a3c4d5e6f71";
    private static final String LEAVE_ID = "665f1c2e8f1b2a3c4d5e6f99";
    private static final Instant NOW = Instant.parse("2026-03-10T09:00:00Z");   // today = 2026-03-10

    @Mock private LeaveRepository leaves;
    @Mock private EmployeeRepository employees;
    @Mock private ActivityService activity;
    @Mock private NotificationService notifications;

    private LeaveService service;
    private final Actor employeeActor = new Actor("priya", Role.EMPLOYEE, "EMP-001");
    private final Actor hrActor = new Actor("hr1", Role.HR, null);

    @BeforeEach
    void setUp() {
        service = new LeaveService(leaves, employees, activity, notifications, Clock.fixed(NOW, ZoneOffset.UTC), 100);
    }

    private static Employee employee(String id, String code) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode(code);
        e.setFullName("Priya Sharma");
        return e;
    }

    private static LeavePayload payload(String start, String end) {
        return new LeavePayload(null, LeaveType.ANNUAL, LocalDate.parse(start), LocalDate.parse(end), "Family trip");
    }

    private static LeaveRequest pending(String requestedBy, String employeeCode) {
        LeaveRequest r = new LeaveRequest();
        r.setId(LEAVE_ID);
        r.setEmployeeId(EMP_ID);
        r.setEmployeeCode(employeeCode);
        r.setEmployeeName("Priya Sharma");
        r.setType(LeaveType.SICK);
        r.setStartDate(LocalDate.parse("2026-03-12"));
        r.setEndDate(LocalDate.parse("2026-03-13"));
        r.setStatus(LeaveStatus.PENDING);
        r.setRequestedBy(requestedBy);
        return r;
    }

    @Test
    void apply_createsPendingRequestWithInclusiveDayCountAndNotifiesStaff() {
        when(employees.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee(EMP_ID, "EMP-001")));
        when(leaves.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            r.setId(LEAVE_ID);
            return r;
        });

        LeaveResponse response = service.apply(employeeActor, payload("2026-03-16", "2026-03-18"));

        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
        assertThat(response.days()).isEqualTo(3);
        assertThat(response.employeeCode()).isEqualTo("EMP-001");
        assertThat(response.requestedBy()).isEqualTo("priya");
        verify(notifications).notifyRoles(eq(Set.of("ADMIN", "HR")), eq("LEAVE_SUBMITTED"), anyString(), eq(LEAVE_ID));
        verify(activity).record(eq("LEAVE_SUBMITTED"), eq("LEAVE"), eq(LEAVE_ID), anyString());
    }

    @Test
    void apply_employeeCannotFileForSomeoneElse() {
        when(employees.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee(EMP_ID, "EMP-001")));
        when(leaves.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        LeavePayload sneaky = new LeavePayload(OTHER_EMP_ID, LeaveType.ANNUAL, LocalDate.parse("2026-03-16"),
                LocalDate.parse("2026-03-16"), "Trying another id");

        LeaveResponse response = service.apply(employeeActor, sneaky);

        assertThat(response.employeeId()).isEqualTo(EMP_ID);
        verify(employees, never()).findById(any());
    }

    @Test
    void apply_staffMustChooseAnEmployee() {
        assertThatThrownBy(() -> service.apply(hrActor, payload("2026-03-16", "2026-03-16")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void apply_staffCanFileOnBehalfOfAnEmployee() {
        when(employees.findById(OTHER_EMP_ID)).thenReturn(Optional.of(employee(OTHER_EMP_ID, "EMP-002")));
        when(leaves.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse response = service.apply(hrActor, new LeavePayload(OTHER_EMP_ID, LeaveType.CASUAL,
                LocalDate.parse("2026-03-16"), LocalDate.parse("2026-03-16"), "Filed by HR"));

        assertThat(response.employeeCode()).isEqualTo("EMP-002");
        assertThat(response.requestedBy()).isEqualTo("hr1");
    }

    @Test
    void apply_unlinkedEmployeeAccountIsRejected() {
        Actor unlinked = new Actor("ghost", Role.EMPLOYEE, null);

        assertThatThrownBy(() -> service.apply(unlinked, payload("2026-03-16", "2026-03-16")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("not linked");
    }

    @Test
    void apply_endBeforeStart_isRejected() {
        when(employees.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee(EMP_ID, "EMP-001")));

        assertThatThrownBy(() -> service.apply(employeeActor, payload("2026-03-18", "2026-03-16")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("End date");
        verify(leaves, never()).save(any());
    }

    @Test
    void apply_tooFarInThePast_isRejected() {
        when(employees.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee(EMP_ID, "EMP-001")));

        assertThatThrownBy(() -> service.apply(employeeActor, payload("2026-01-01", "2026-01-02")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("past");
    }

    @Test
    void apply_longerThanNinetyDays_isRejected() {
        when(employees.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee(EMP_ID, "EMP-001")));

        assertThatThrownBy(() -> service.apply(employeeActor, payload("2026-03-16", "2026-07-30")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("90");
    }

    @Test
    void apply_overlappingRequest_isRejectedWithConflict() {
        when(employees.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee(EMP_ID, "EMP-001")));
        when(leaves.findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(EMP_ID), any(), any(), any())).thenReturn(List.of(pending("priya", "EMP-001")));

        assertThatThrownBy(() -> service.apply(employeeActor, payload("2026-03-12", "2026-03-14")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(leaves, never()).save(any());
    }

    @Test
    void approve_setsDecisionFieldsAndNotifiesRequester() {
        when(leaves.findById(LEAVE_ID)).thenReturn(Optional.of(pending("priya", "EMP-001")));
        when(leaves.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse response = service.decide(hrActor, LEAVE_ID, true, " Enjoy ");

        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.decidedBy()).isEqualTo("hr1");
        assertThat(response.decidedAt()).isEqualTo(NOW);
        assertThat(response.decisionNote()).isEqualTo("Enjoy");
        verify(notifications).notifyUser(eq("priya"), eq("LEAVE_APPROVED"), anyString(), eq(LEAVE_ID));
        verify(activity).record(eq("LEAVE_APPROVED"), eq("LEAVE"), eq(LEAVE_ID), anyString());
    }

    @Test
    void reject_setsRejectedStatus() {
        when(leaves.findById(LEAVE_ID)).thenReturn(Optional.of(pending("priya", "EMP-001")));
        when(leaves.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.decide(hrActor, LEAVE_ID, false, null).status()).isEqualTo(LeaveStatus.REJECTED);
    }

    @Test
    void decide_employeeRoleIsForbidden() {
        assertThatThrownBy(() -> service.decide(employeeActor, LEAVE_ID, true, null)).isInstanceOf(ForbiddenException.class);
        verify(leaves, never()).findById(any());
    }

    @Test
    void decide_cannotDecideOwnRequest() {
        when(leaves.findById(LEAVE_ID)).thenReturn(Optional.of(pending("hr1", "EMP-009")));

        assertThatThrownBy(() -> service.decide(hrActor, LEAVE_ID, true, null)).isInstanceOf(ForbiddenException.class);
        verify(leaves, never()).save(any());
    }

    @Test
    void decide_alreadyDecidedRequest_isConflict() {
        LeaveRequest r = pending("priya", "EMP-001");
        r.setStatus(LeaveStatus.APPROVED);
        when(leaves.findById(LEAVE_ID)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.decide(hrActor, LEAVE_ID, false, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void onLeaveToday_queriesApprovedRequestsCoveringToday() {
        LeaveRequest r = pending("priya", "EMP-001");
        r.setStatus(LeaveStatus.APPROVED);
        when(leaves.findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                LeaveStatus.APPROVED, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))).thenReturn(List.of(r));

        assertThat(service.onLeaveToday()).hasSize(1);
    }
}
