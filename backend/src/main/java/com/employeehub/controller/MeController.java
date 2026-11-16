package com.employeehub.controller;

import com.employeehub.dto.EmployeeResponse;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.security.Actor;
import com.employeehub.security.ActorResolver;
import com.employeehub.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "My profile", description = "Personal data for the signed-in user")
public class MeController {

    private final EmployeeService employees;
    private final ActorResolver actors;

    public MeController(EmployeeService employees, ActorResolver actors) {
        this.employees = employees;
        this.actors = actors;
    }

    @Operation(summary = "My employee record", description = "Available to every role whose account is linked to an employee code.")
    @GetMapping("/employee")
    public EmployeeResponse myEmployee() {
        Actor actor = actors.current();
        if (actor.employeeCode() == null) {
            throw new ResourceNotFoundException("Your account is not linked to an employee record");
        }
        return employees.getByEmployeeCode(actor.employeeCode());
    }
}
