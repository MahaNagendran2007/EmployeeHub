package com.employeehub.controller;

import com.employeehub.dto.ActivityResponse;
import com.employeehub.dto.PageResponse;
import com.employeehub.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@Tag(name = "Activity", description = "Audit trail (ADMIN/HR)")
public class ActivityController {

    private final ActivityService service;

    public ActivityController(ActivityService service) { this.service = service; }

    @Operation(summary = "Recent activity, newest first (page size capped at 100)")
    @GetMapping
    public PageResponse<ActivityResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
}
