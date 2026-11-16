package com.employeehub.controller;

import com.employeehub.dto.CountItem;
import com.employeehub.dto.EmployeeRequest;
import com.employeehub.dto.EmployeeResponse;
import com.employeehub.dto.EmployeeStatsResponse;
import com.employeehub.dto.ErrorResponse;
import com.employeehub.dto.ImportResult;
import com.employeehub.dto.PageResponse;
import com.employeehub.dto.TrendPoint;
import com.employeehub.exception.BadRequestException;
import com.employeehub.model.EmploymentStatus;
import com.employeehub.repository.EmployeeSearchCriteria;
import com.employeehub.service.EmployeeImportService;
import com.employeehub.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@Tag(name = "Employees", description = "Employee management operations (ADMIN/HR; delete is ADMIN only)")
public class EmployeeController {

    private final EmployeeService service;
    private final EmployeeImportService importService;

    public EmployeeController(EmployeeService service, EmployeeImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    @Operation(summary = "Create an employee",
            description = "Validates the body, enforces unique employeeCode and email, and returns the created record.")
    @ApiResponse(responseCode = "201", description = "Employee created")
    @ApiResponse(responseCode = "400", description = "Validation failed or malformed body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Employee code or email already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "List employees",
            description = "Paginated and sorted. Optional case-insensitive search over name, code, email and department, "
                    + "plus exact-match filters. Page size is capped at 100.")
    @ApiResponse(responseCode = "200", description = "A page of employees (empty content when nothing matches)")
    @ApiResponse(responseCode = "400", description = "Invalid sort field, direction or filter value",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<EmployeeResponse> list(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "asc or desc") @RequestParam(defaultValue = "desc") String direction,
            @Parameter(description = "Matches name, employee code or email") @RequestParam(required = false) String search,
            @Parameter(description = "Exact department") @RequestParam(required = false) String department,
            @Parameter(description = "Exact location") @RequestParam(required = false) String location,
            @Parameter(description = "ACTIVE, ON_LEAVE or INACTIVE") @RequestParam(required = false) EmploymentStatus status,
            @Parameter(description = "Exact job title (designation)") @RequestParam(required = false) String designation) {
        Sort.Direction sortDirection = Sort.Direction.fromOptionalString(direction)
                .orElseThrow(() -> new BadRequestException("direction must be 'asc' or 'desc'"));
        return service.list(new EmployeeSearchCriteria(search, department, location, status, designation),
                page, size, sortBy, sortDirection);
    }

    @Operation(summary = "Export matching employees as CSV",
            description = "Same search/filter/sort parameters as the list endpoint, but returns every match (max 50,000). Emergency-contact data is not exported.")
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "fullName") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) String designation) {
        Sort.Direction sortDirection = Sort.Direction.fromOptionalString(direction)
                .orElseThrow(() -> new BadRequestException("direction must be 'asc' or 'desc'"));
        String csv = service.exportCsv(new EmployeeSearchCriteria(search, department, location, status, designation),
                sortBy, sortDirection);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("employees.csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "Import employees from a CSV file",
            description = "multipart/form-data with a 'file' part (max 2 MB, 1000 rows). Required columns: employeeCode, fullName, email, department, jobTitle, location, joiningDate (yyyy-MM-dd). Optional: phone, employmentStatus, reportingManager. Valid rows are saved; the response lists every rejected row.")
    @ApiResponse(responseCode = "200", description = "Import summary")
    @ApiResponse(responseCode = "400", description = "Empty file or missing columns",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("Choose a CSV file to import");
        }
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return importService.importCsv(text);
    }

    @Operation(summary = "Employees joined per month",
            description = "Last N months (default 12, max 36), zero-filled, aggregated in MongoDB. Employees without a joining date are ignored.")
    @GetMapping("/trends")
    public List<TrendPoint> trends(@RequestParam(defaultValue = "12") int months) {
        return service.joiningTrend(months);
    }

    @Operation(summary = "Dashboard statistics",
            description = "Total employees, counts per employment status and counts per department.")
    @ApiResponse(responseCode = "200", description = "Statistics (zeros when the database is empty)")
    @GetMapping("/stats")
    public EmployeeStatsResponse stats() {
        return service.stats();
    }

    @Operation(summary = "Distinct job titles (designations)", description = "Feeds the designation filter.")
    @GetMapping("/designations")
    public List<String> designations() {
        return service.designations();
    }

    @Operation(summary = "Employee count per location")
    @ApiResponse(responseCode = "200", description = "Distinct locations with counts, largest first")
    @GetMapping("/locations")
    public List<CountItem> locations() {
        return service.locations();
    }

    @Operation(summary = "Get an employee by id")
    @ApiResponse(responseCode = "200", description = "Employee found")
    @ApiResponse(responseCode = "400", description = "Id is not a valid MongoDB ObjectId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Employee not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public EmployeeResponse getById(@PathVariable String id) {
        return service.getById(id);
    }

    @Operation(summary = "Update an employee", description = "Full replacement of all editable fields.")
    @ApiResponse(responseCode = "200", description = "Employee updated")
    @ApiResponse(responseCode = "400", description = "Validation failed or invalid id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Employee not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Employee code or email belongs to another employee",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable String id, @Valid @RequestBody EmployeeRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Delete an employee")
    @ApiResponse(responseCode = "204", description = "Employee deleted")
    @ApiResponse(responseCode = "400", description = "Invalid id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Employee not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
