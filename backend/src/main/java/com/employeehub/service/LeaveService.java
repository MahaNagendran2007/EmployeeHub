package com.employeehub.service;

import com.employeehub.dto.FieldErrorDetail;
import com.employeehub.dto.LeavePayload;
import com.employeehub.dto.LeaveResponse;
import com.employeehub.dto.LeaveSummaryResponse;
import com.employeehub.dto.PageResponse;
import com.employeehub.exception.BadRequestException;
import com.employeehub.exception.ConflictException;
import com.employeehub.exception.DuplicateResourceException;
import com.employeehub.exception.ForbiddenException;
import com.employeehub.exception.InvalidIdException;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Employee;
import com.employeehub.model.LeaveRequest;
import com.employeehub.model.LeaveStatus;
import com.employeehub.model.LeaveType;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.LeaveRepository;
import com.employeehub.security.Actor;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Leave rules: end date not before start; at most 90 days; start no earlier than 30 days ago;
 * no overlap with the same employee's PENDING or APPROVED requests; only PENDING requests can be decided;
 * nobody can decide their own request. Days are counted as inclusive calendar days (no holiday/weekend policy).
 * Leave balances are intentionally not modelled because no leave policy exists.
 */
@Service
public class LeaveService {

    static final int MAX_DAYS = 90;
    static final int MAX_BACKDATE_DAYS = 30;

    private final LeaveRepository leaves;
    private final EmployeeRepository employees;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;
    private final int maxPageSize;

    public LeaveService(LeaveRepository leaves, EmployeeRepository employees, ActivityService activity,
                        NotificationService notifications, Clock clock,
                        @Value("${app.pagination.max-page-size:100}") int maxPageSize) {
        this.leaves = leaves;
        this.employees = employees;
        this.activity = activity;
        this.notifications = notifications;
        this.clock = clock;
        this.maxPageSize = maxPageSize;
    }

    private LocalDate today() { return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC); }

    public LeaveResponse apply(Actor actor, LeavePayload p) {
        Employee employee = resolveEmployee(actor, p.employeeId());
        validateDates(p.startDate(), p.endDate());

        List<LeaveRequest> overlapping = leaves
                .findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getId(), Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), p.endDate(), p.startDate());
        if (!overlapping.isEmpty()) {
            throw new DuplicateResourceException(List.of(new FieldErrorDetail("startDate",
                    "This overlaps an existing " + overlapping.get(0).getStatus().name().toLowerCase() + " leave request")));
        }

        LeaveRequest r = new LeaveRequest();
        r.setEmployeeId(employee.getId());
        r.setEmployeeCode(employee.getEmployeeCode());
        r.setEmployeeName(employee.getFullName());
        r.setType(p.type());
        r.setStartDate(p.startDate());
        r.setEndDate(p.endDate());
        r.setDays((int) ChronoUnit.DAYS.between(p.startDate(), p.endDate()) + 1);
        r.setReason(p.reason().trim());
        r.setStatus(LeaveStatus.PENDING);
        r.setRequestedBy(actor.username());
        r.setCreatedAt(clock.instant());
        LeaveRequest saved = leaves.save(r);

        activity.record("LEAVE_SUBMITTED", "LEAVE", saved.getId(),
                employee.getEmployeeCode() + " requested " + saved.getDays() + " day(s) of " + saved.getType() + " leave");
        notifications.notifyRoles(Set.of("ADMIN", "HR"), "LEAVE_SUBMITTED",
                employee.getFullName() + " requested " + saved.getDays() + " day(s) of " + saved.getType().name().toLowerCase() + " leave",
                saved.getId());
        return toResponse(saved);
    }

    private Employee resolveEmployee(Actor actor, String employeeId) {
        if (!actor.isStaff() || employeeId == null || employeeId.isBlank()) {
            // EMPLOYEE accounts can only file for themselves; staff without an explicit employee use their own link.
            if (actor.employeeCode() == null) {
                throw new BadRequestException(actor.isStaff()
                        ? "Choose the employee this leave is for"
                        : "Your account is not linked to an employee record");
            }
            return employees.findByEmployeeCode(actor.employeeCode())
                    .orElseThrow(() -> new BadRequestException("Your linked employee record was not found"));
        }
        if (!ObjectId.isValid(employeeId)) {
            throw new InvalidIdException(employeeId);
        }
        return employees.findById(employeeId).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BadRequestException("End date cannot be before the start date");
        }
        if (start.isBefore(today().minusDays(MAX_BACKDATE_DAYS))) {
            throw new BadRequestException("Leave cannot start more than " + MAX_BACKDATE_DAYS + " days in the past");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BadRequestException("A single leave request cannot exceed " + MAX_DAYS + " days");
        }
    }

    public PageResponse<LeaveResponse> list(LeaveStatus status, String employeeId, int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<LeaveRequest> result;
        if (employeeId != null && !employeeId.isBlank()) {
            result = status == null ? leaves.findByEmployeeId(employeeId, pageable)
                    : leaves.findByEmployeeIdAndStatus(employeeId, status, pageable);
        } else {
            result = status == null ? leaves.findAll(pageable) : leaves.findByStatus(status, pageable);
        }
        return PageResponse.from(result.map(this::toResponse));
    }

    /** Leave history of the caller's own linked employee record. */
    public PageResponse<LeaveResponse> mine(Actor actor, int page, int size) {
        if (actor.employeeCode() == null) {
            return PageResponse.from(Page.empty(pageable(page, size)));
        }
        Employee employee = employees.findByEmployeeCode(actor.employeeCode()).orElse(null);
        if (employee == null) {
            return PageResponse.from(Page.empty(pageable(page, size)));
        }
        return PageResponse.from(leaves.findByEmployeeId(employee.getId(), pageable(page, size)).map(this::toResponse));
    }

    public LeaveResponse decide(Actor actor, String id, boolean approve, String note) {
        if (!actor.isStaff()) {
            throw new ForbiddenException("Only ADMIN or HR can decide leave requests");
        }
        if (!ObjectId.isValid(id)) {
            throw new InvalidIdException(id);
        }
        LeaveRequest r = leaves.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        if (actor.username().equals(r.getRequestedBy()) || (actor.employeeCode() != null && actor.employeeCode().equals(r.getEmployeeCode()))) {
            throw new ForbiddenException("You cannot decide your own leave request");
        }
        if (r.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException("This request has already been " + r.getStatus().name().toLowerCase());
        }
        r.setStatus(approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED);
        r.setDecidedBy(actor.username());
        r.setDecidedAt(clock.instant());
        r.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
        LeaveRequest saved = leaves.save(r);

        String verb = approve ? "approved" : "rejected";
        activity.record(approve ? "LEAVE_APPROVED" : "LEAVE_REJECTED", "LEAVE", saved.getId(),
                "Leave for " + saved.getEmployeeCode() + " " + verb);
        notifications.notifyUser(saved.getRequestedBy(), approve ? "LEAVE_APPROVED" : "LEAVE_REJECTED",
                "Your " + saved.getType().name().toLowerCase() + " leave (" + saved.getStartDate() + " to " + saved.getEndDate() + ") was " + verb,
                saved.getId());
        return toResponse(saved);
    }

    /** Approved leave that covers today (drives the dashboard "on leave today" panel). */
    public List<LeaveResponse> onLeaveToday() {
        LocalDate day = today();
        return leaves.findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(LeaveStatus.APPROVED, day, day)
                .stream().map(this::toResponse).toList();
    }

    public LeaveSummaryResponse summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (LeaveStatus s : LeaveStatus.values()) {
            byStatus.put(s.name(), leaves.countByStatus(s));
        }
        Map<LeaveType, Long> typeCounts = new EnumMap<>(LeaveType.class);
        long approvedDays = 0;
        long total = 0;
        for (LeaveRequest r : leaves.findAll()) {
            total++;
            typeCounts.merge(r.getType(), 1L, Long::sum);
            if (r.getStatus() == LeaveStatus.APPROVED) {
                approvedDays += r.getDays();
            }
        }
        Map<String, Long> byType = new LinkedHashMap<>();
        for (LeaveType t : LeaveType.values()) {
            byType.put(t.name(), typeCounts.getOrDefault(t, 0L));
        }
        return new LeaveSummaryResponse(total, byStatus, byType, approvedDays);
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), maxPageSize),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private LeaveResponse toResponse(LeaveRequest r) {
        return new LeaveResponse(r.getId(), r.getEmployeeId(), r.getEmployeeCode(), r.getEmployeeName(), r.getType(),
                r.getStartDate(), r.getEndDate(), r.getDays(), r.getReason(), r.getStatus(), r.getRequestedBy(),
                r.getCreatedAt(), r.getDecidedBy(), r.getDecidedAt(), r.getDecisionNote());
    }
}
