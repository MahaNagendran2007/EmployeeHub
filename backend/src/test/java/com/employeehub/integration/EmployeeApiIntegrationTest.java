package com.employeehub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employeehub.repository.EmployeeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the full stack against a throw-away MongoDB container. It never touches a developer's database.
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "it-admin", roles = "ADMIN")
@Testcontainers(disabledWithoutDocker = true)
class EmployeeApiIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EmployeeRepository repository;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private static String body(String code, String name, String email, String dept, String location, String status) {
        return """
                {"employeeCode":"%s","fullName":"%s","email":"%s","department":"%s","jobTitle":"Engineer",
                 "location":"%s","joiningDate":"2024-07-01","employmentStatus":"%s"}
                """.formatted(code, name, email, dept, location, status);
    }

    private String create(String json) throws Exception {
        String response = mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void seedThree() throws Exception {
        create(body("EMP-1", "Alice Brown", "alice@example.com", "Engineering", "Chennai", "ACTIVE"));
        create(body("EMP-2", "Bob Stone", "bob@example.com", "Engineering", "Delhi", "ON_LEAVE"));
        create(body("EMP-3", "Carol King", "carol@example.com", "Sales", "Chennai", "ACTIVE"));
    }

    @Test
    void uniqueIndexesAreCreatedOnStartup() {
        var uniqueFields = mongoTemplate.indexOps("employees").getIndexInfo().stream()
                .filter(IndexInfo::isUnique)
                .flatMap(info -> info.getIndexFields().stream())
                .map(field -> field.getKey())
                .toList();

        assertThat(uniqueFields).contains("email", "employeeCode");
    }

    @Test
    void createThenReadBack_persistsFieldsAndDate() throws Exception {
        String id = create(body("emp-9", "Dana Fox", "Dana@Example.com", "Design", "Mumbai", "ACTIVE"));

        mockMvc.perform(get("/api/employees/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("EMP-9"))
                .andExpect(jsonPath("$.email").value("dana@example.com"))
                .andExpect(jsonPath("$.joiningDate").value("2024-07-01"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void duplicateEmailAndCode_return409() throws Exception {
        create(body("EMP-1", "Alice Brown", "alice@example.com", "Engineering", "Chennai", "ACTIVE"));

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content(body("EMP-2", "Other", "ALICE@example.com", "Sales", "Delhi", "ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content(body("emp-1", "Other", "other@example.com", "Sales", "Delhi", "ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("employeeCode"));
    }

    @Test
    void pagination_splitsResultsAndReportsTotals() throws Exception {
        seedThree();

        mockMvc.perform(get("/api/employees").param("size", "2").param("page", "0")
                        .param("sortBy", "fullName").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].fullName").value("Alice Brown"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/employees").param("size", "2").param("page", "1")
                        .param("sortBy", "fullName").param("direction", "asc"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Carol King"))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void searchAndFilters_narrowResults() throws Exception {
        seedThree();

        mockMvc.perform(get("/api/employees").param("search", "BOB"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeCode").value("EMP-2"));

        mockMvc.perform(get("/api/employees").param("search", "emp-3"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/employees").param("search", "carol@example"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/employees").param("department", "Engineering").param("location", "Chennai"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Alice Brown"));

        mockMvc.perform(get("/api/employees").param("status", "ON_LEAVE"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Regex metacharacters in the search text are treated literally, not as a pattern.
        mockMvc.perform(get("/api/employees").param("search", ".*"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void update_changesRecordAndRefreshesUpdatedAt() throws Exception {
        String id = create(body("EMP-1", "Alice Brown", "alice@example.com", "Engineering", "Chennai", "ACTIVE"));

        mockMvc.perform(put("/api/employees/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("EMP-1", "Alice Green", "alice@example.com", "Sales", "Delhi", "INACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice Green"))
                .andExpect(jsonPath("$.employmentStatus").value("INACTIVE"));

        JsonNode reloaded = objectMapper.readTree(mockMvc.perform(get("/api/employees/" + id))
                .andReturn().getResponse().getContentAsString());
        assertThat(reloaded.get("department").asText()).isEqualTo("Sales");
        assertThat(reloaded.get("updatedAt").asText()).isNotEqualTo(reloaded.get("createdAt").asText());
    }

    @Test
    void delete_removesRecord_thenReturns404() throws Exception {
        String id = create(body("EMP-1", "Alice Brown", "alice@example.com", "Engineering", "Chennai", "ACTIVE"));

        mockMvc.perform(delete("/api/employees/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/employees/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/employees/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void statsAndLocations_aggregateInTheDatabase() throws Exception {
        seedThree();

        mockMvc.perform(get("/api/employees/stats"))
                .andExpect(jsonPath("$.totalEmployees").value(3))
                .andExpect(jsonPath("$.byStatus.ACTIVE").value(2))
                .andExpect(jsonPath("$.byStatus.ON_LEAVE").value(1))
                .andExpect(jsonPath("$.byStatus.INACTIVE").value(0))
                .andExpect(jsonPath("$.byDepartment[0].name").value("Engineering"))
                .andExpect(jsonPath("$.byDepartment[0].count").value(2));

        mockMvc.perform(get("/api/employees/locations"))
                .andExpect(jsonPath("$[0].name").value("Chennai"))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[1].name").value("Delhi"));
    }

    @Test
    void emptyDatabase_returnsZeroedStatsAndEmptyPage() throws Exception {
        mockMvc.perform(get("/api/employees/stats"))
                .andExpect(jsonPath("$.totalEmployees").value(0))
                .andExpect(jsonPath("$.byStatus.ACTIVE").value(0));
        mockMvc.perform(get("/api/employees"))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/employees/locations")).andExpect(jsonPath("$.length()").value(0));
    }
}
