package com.employeehub.controller;

import com.employeehub.dto.LeaveDecision;
import com.employeehub.dto.LeavePayload;
import com.employeehub.dto.LeaveResponse;
import com.employeehub.dto.LeaveSummaryResponse;
import com.employeehub.dto.PageResponse;
import com.employeehub.model.LeaveStatus;
import com.employeehub.security.ActorResolver;
import com.employeehub.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaves")
@Tag(name = "Leave", description = "Leave requests, approvals and history")
public class LeaveController {

    private final LeaveService service;
    private final ActorResolver actors;

    public LeaveController(LeaveService service, ActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @Operation(summary = "Apply for leave",
            description = "EMPLOYEE accounts always file for their own linked employee. ADMIN/HR may pass employeeId to file on behalf of someone. 409 when dates overlap a pending/approved request.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse apply(@Valid @RequestBody LeavePayload payload) {
        return service.apply(actors.current(), payload);
    }

    @Operation(summary = "All leave requests (ADMIN/HR)", description = "Optional status and employeeId filters, newest first.")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @GetMapping
    public PageResponse<LeaveResponse> list(@RequestParam(required = false) LeaveStatus status,
                                            @RequestParam(required = false) String employeeId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return service.list(status, employeeId, page, size);
    }

    @Operation(summary = "My leave history")
    @GetMapping("/mine")
    public PageResponse<LeaveResponse> mine(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return service.mine(actors.current(), page, size);
    }

    @Operation(summary = "Approve a pending request (ADMIN/HR, not your own)")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @PostMapping("/{id}/approve")
    public LeaveResponse approve(@PathVariable String id, @Valid @RequestBody(required = false) LeaveDecision decision) {
        return service.decide(actors.current(), id, true, decision == null ? null : decision.note());
    }

    @Operation(summary = "Reject a pending request (ADMIN/HR, not your own)")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @PostMapping("/{id}/reject")
    public LeaveResponse reject(@PathVariable String id, @Valid @RequestBody(required = false) LeaveDecision decision) {
        return service.decide(actors.current(), id, false, decision == null ? null : decision.note());
    }

    @Operation(summary = "Employees on approved leave today")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @GetMapping("/on-leave-today")
    public List<LeaveResponse> onLeaveToday() { return service.onLeaveToday(); }

    @Operation(summary = "Leave summary counts by status and type")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @GetMapping("/summary")
    public LeaveSummaryResponse summary() { return service.summary(); }
}
