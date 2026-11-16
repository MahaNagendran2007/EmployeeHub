package com.employeehub.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employeehub.dto.CountItem;
import com.employeehub.dto.EmployeeResponse;
import com.employeehub.dto.EmployeeStatsResponse;
import com.employeehub.dto.FieldErrorDetail;
import com.employeehub.dto.PageResponse;
import com.employeehub.dto.ImportResult;
import com.employeehub.exception.DuplicateResourceException;
import com.employeehub.exception.ForbiddenException;
import com.employeehub.exception.InvalidIdException;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.EmploymentStatus;
import com.employeehub.service.EmployeeImportService;
import com.employeehub.service.EmployeeService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false)   // HTTP behaviour only; security rules are covered by SecurityIntegrationTest
class EmployeeControllerTest {

    private static final String ID = "665f1c2e8f1b2a3c4d5e6f70";
    private static final String VALID_BODY = """
            {"employeeCode":"EMP-001","fullName":"Priya Sharma","email":"priya@example.com",
             "department":"Engineering","jobTitle":"Engineer","location":"Chennai",
             "phone":"+91 98765 43210","joiningDate":"2024-07-01","employmentStatus":"ACTIVE"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService service;

    @MockBean
    private EmployeeImportService importService;

    private static EmployeeResponse sample() {
        return new EmployeeResponse(ID, "EMP-001", "Priya Sharma", "priya@example.com", "Engineering",
                "Engineer", "Chennai", "+91 98765 43210", LocalDate.of(2024, 7, 1), EmploymentStatus.ACTIVE,
                Instant.parse("2026-01-15T10:00:00Z"), Instant.parse("2026-01-15T10:00:00Z"), null, null, null);
    }

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        when(service.create(any())).thenReturn(sample());

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/employees/" + ID)))
                .andExpect(jsonPath("$.id").value(ID))
                .andExpect(jsonPath("$.joiningDate").value("2024-07-01"))
                .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"));
    }

    @Test
    void create_invalidBody_returns400WithFieldErrors() throws Exception {
        String invalid = """
                {"employeeCode":"x","fullName":"","email":"not-an-email","department":"","jobTitle":"",
                 "location":"","joiningDate":null,"employmentStatus":null}
                """;

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("employeeCode", "fullName",
                        "email", "department", "jobTitle", "location", "joiningDate", "employmentStatus")));
        verifyNoInteractions(service);
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_unknownStatusValue_returns400() throws Exception {
        String body = VALID_BODY.replace("ACTIVE", "RETIRED");

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicate_returns409WithFieldErrors() throws Exception {
        when(service.create(any())).thenThrow(
                new DuplicateResourceException(List.of(new FieldErrorDetail("email", "Email is already in use"))));

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void getById_returnsEmployee() throws Exception {
        when(service.getById(ID)).thenReturn(sample());

        mockMvc.perform(get("/api/employees/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Priya Sharma"));
    }

    @Test
    void getById_missing_returns404() throws Exception {
        when(service.getById(ID)).thenThrow(new ResourceNotFoundException("Employee with id '" + ID + "' was not found"));

        mockMvc.perform(get("/api/employees/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getById_invalidId_returns400() throws Exception {
        when(service.getById("abc")).thenThrow(new InvalidIdException("abc"));

        mockMvc.perform(get("/api/employees/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void list_passesPaginationSortAndFilters() throws Exception {
        when(service.list(any(), anyInt(), anyInt(), any(), any())).thenReturn(
                new PageResponse<>(List.of(sample()), 1, 5, 11, 3, false, false));

        mockMvc.perform(get("/api/employees").param("page", "1").param("size", "5")
                        .param("sortBy", "fullName").param("direction", "asc")
                        .param("search", "priya").param("department", "Engineering")
                        .param("location", "Chennai").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].employeeCode").value("EMP-001"))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));

        org.mockito.Mockito.verify(service).list(
                org.mockito.ArgumentMatchers.argThat(c -> "priya".equals(c.search())
                        && "Engineering".equals(c.department()) && "Chennai".equals(c.location())
                        && c.status() == EmploymentStatus.ACTIVE),
                eq(1), eq(5), eq("fullName"), eq(Sort.Direction.ASC));
    }

    @Test
    void list_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/employees").param("status", "RETIRED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }

    @Test
    void list_invalidDirection_returns400() throws Exception {
        mockMvc.perform(get("/api/employees").param("direction", "sideways"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void stats_returnsTotalsAndBreakdowns() throws Exception {
        when(service.stats()).thenReturn(new EmployeeStatsResponse(3,
                Map.of("ACTIVE", 2L, "ON_LEAVE", 1L, "INACTIVE", 0L),
                List.of(new CountItem("Engineering", 3))));

        mockMvc.perform(get("/api/employees/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(3))
                .andExpect(jsonPath("$.byStatus.ACTIVE").value(2))
                .andExpect(jsonPath("$.byDepartment[0].name").value("Engineering"));
    }

    @Test
    void locations_returnsSummary() throws Exception {
        when(service.locations()).thenReturn(List.of(new CountItem("Chennai", 4), new CountItem("Delhi", 1)));

        mockMvc.perform(get("/api/employees/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chennai"))
                .andExpect(jsonPath("$[0].count").value(4));
    }

    @Test
    void update_returns200() throws Exception {
        when(service.update(eq(ID), any())).thenReturn(sample());

        mockMvc.perform(put("/api/employees/" + ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID));
    }

    @Test
    void update_missing_returns404() throws Exception {
        when(service.update(eq(ID), any())).thenThrow(new ResourceNotFoundException("missing"));

        mockMvc.perform(put("/api/employees/" + ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/employees/" + ID)).andExpect(status().isNoContent());
    }

    @Test
    void delete_missing_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("missing")).when(service).delete(ID);

        mockMvc.perform(delete("/api/employees/" + ID)).andExpect(status().isNotFound());
    }

    @Test
    void unexpectedFailure_returns500WithoutLeakingDetails() throws Exception {
        when(service.getById(ID)).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/api/employees/" + ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void unsupportedMethod_returns405InStandardFormat() throws Exception {
        mockMvc.perform(delete("/api/employees"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void everyErrorResponseSharesTheSameShape() throws Exception {
        when(service.getById("abc")).thenThrow(new InvalidIdException("abc"));
        when(service.getById(ID)).thenThrow(new ResourceNotFoundException("missing"));

        for (var request : List.of(
                get("/api/employees/abc"),
                get("/api/employees/" + ID),
                post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/employees"))) {
            mockMvc.perform(request)
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.path").exists())
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }
    }

    @Test
    void forbiddenFromServiceMapsTo403() throws Exception {
        doThrow(new ForbiddenException("no")).when(service).delete(ID);

        mockMvc.perform(delete("/api/employees/" + ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void trends_returnsMonthlyPoints() throws Exception {
        when(service.joiningTrend(6)).thenReturn(List.of(new com.employeehub.dto.TrendPoint("2026-01", 2)));

        mockMvc.perform(get("/api/employees/trends").param("months", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value("2026-01"))
                .andExpect(jsonPath("$[0].count").value(2));
    }

    @Test
    void export_returnsCsvAttachment() throws Exception {
        when(service.exportCsv(any(), any(), any())).thenReturn("employeeCode,fullName\r\nEMP-1,A\r\n");

        mockMvc.perform(get("/api/employees/export").param("search", "a"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("employees.csv")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.startsWith("employeeCode")));
    }

    @Test
    void import_returnsSummary() throws Exception {
        when(importService.importCsv(any())).thenReturn(new ImportResult(2, 1, 1,
                List.of(new ImportResult.RowError(3, "bad")), false));
        var file = new org.springframework.mock.web.MockMultipartFile("file", "e.csv", "text/csv", "a,b\n1,2\n".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/employees/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(3));
    }

    @Test
    void import_emptyFile_returns400() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "e.csv", "text/csv", new byte[0]);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/employees/import").file(file))
                .andExpect(status().isBadRequest());
    }
}
