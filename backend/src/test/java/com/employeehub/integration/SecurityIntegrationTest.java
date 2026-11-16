package com.employeehub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employeehub.model.Employee;
import com.employeehub.model.EmploymentStatus;
import com.employeehub.model.Role;
import com.employeehub.model.UserAccount;
import com.employeehub.repository.ActivityRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.LeaveRepository;
import com.employeehub.repository.NotificationRepository;
import com.employeehub.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Authentication, role-based authorization and the leave workflow against a disposable MongoDB. Skipped without Docker. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SecurityIntegrationTest {

    private static final String PASSWORD = "Password-1234";

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private EmployeeRepository employees;
    @Autowired private LeaveRepository leaves;
    @Autowired private ActivityRepository activity;
    @Autowired private NotificationRepository notifications;
    @Autowired private PasswordEncoder encoder;

    private String employeeId;

    @BeforeEach
    void seed() {
        users.deleteAll();
        employees.deleteAll();
        leaves.deleteAll();
        activity.deleteAll();
        notifications.deleteAll();
        employeeId = employee("EMP-001", "priya@example.com").getId();
        employee("EMP-002", "ravi@example.com");
        user("admin1", Role.ADMIN, null);
        user("hr1", Role.HR, null);
        user("priya", Role.EMPLOYEE, "EMP-001");
    }

    private Employee employee(String code, String email) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFullName("Person " + code);
        e.setEmail(email);
        e.setDepartment("Engineering");
        e.setJobTitle("Dev");
        e.setLocation("Chennai");
        e.setJoiningDate(LocalDate.of(2024, 1, 1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return employees.save(e);
    }

    private void user(String username, Role role, String code) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setDisplayName(username);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setRole(role);
        u.setEmployeeCode(code);
        u.setEnabled(true);
        u.setCreatedAt(Instant.now());
        users.save(u);
    }

    private String bearer(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("token").asText();
    }

    private static String leaveJson(LocalDate start, LocalDate end) {
        return "{\"type\":\"ANNUAL\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\",\"reason\":\"Family function\"}";
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/employees").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndSwaggerAreAvailableWithoutLogin() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void login_rejectsBadCredentialsWithGenericMessageAndNeverReturnsPasswordHash() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hr1\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        String ok = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hr1\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("HR"))
                .andReturn().getResponse().getContentAsString();
        assertThat(ok).doesNotContain("passwordHash").doesNotContain("$2");
    }

    @Test
    void rolesControlEmployeeEndpoints() throws Exception {
        String employeeToken = bearer("priya");
        String hrToken = bearer("hr1");
        String adminToken = bearer("admin1");

        mvc.perform(get("/api/employees").header("Authorization", employeeToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/employees").header("Authorization", hrToken)).andExpect(status().isOk());
        mvc.perform(delete("/api/employees/" + employeeId).header("Authorization", hrToken)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/employees/" + employeeId).header("Authorization", adminToken)).andExpect(status().isNoContent());
    }

    @Test
    void employeeCanReadOnlyTheirOwnRecord() throws Exception {
        mvc.perform(get("/api/me/employee").header("Authorization", bearer("priya")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.employeeCode").value("EMP-001"));
    }

    @Test
    void onlyAdminsManageUsers() throws Exception {
        String body = "{\"username\":\"newhr\",\"displayName\":\"New HR\",\"password\":\"Another-Pass-1\",\"role\":\"HR\"}";
        mvc.perform(post("/api/users").header("Authorization", bearer("hr1")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users").header("Authorization", bearer("admin1")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/users").header("Authorization", bearer("admin1")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/users").header("Authorization", bearer("admin1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"weak\",\"displayName\":\"W\",\"password\":\"short\",\"role\":\"HR\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leaveWorkflow_applyOverlapApproveNotifyAndAudit() throws Exception {
        LocalDate start = LocalDate.now(ZoneOffset.UTC).plusDays(5);
        LocalDate end = start.plusDays(2);
        String employeeToken = bearer("priya");
        String hrToken = bearer("hr1");

        String created = mvc.perform(post("/api/leaves").header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(leaveJson(start, end)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.days").value(3)).andReturn().getResponse().getContentAsString();
        String leaveId = mapper.readTree(created).get("id").asText();

        mvc.perform(post("/api/leaves").header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(leaveJson(start.plusDays(1), end.plusDays(1))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/leaves/" + leaveId + "/approve").header("Authorization", employeeToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/notifications/unread-count").header("Authorization", hrToken))
                .andExpect(jsonPath("$.count").value(1));

        mvc.perform(post("/api/leaves/" + leaveId + "/approve").header("Authorization", hrToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Enjoy\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/leaves/" + leaveId + "/reject").header("Authorization", hrToken))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/notifications/unread-count").header("Authorization", employeeToken))
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(get("/api/leaves/mine").header("Authorization", employeeToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/leaves").header("Authorization", employeeToken)).andExpect(status().isForbidden());

        mvc.perform(get("/api/activity").header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action=='LEAVE_APPROVED')]").exists())
                .andExpect(jsonPath("$.content[?(@.action=='LEAVE_SUBMITTED')]").exists());
    }

    @Test
    void approvedLeaveCoveringTodayAppearsInOnLeaveToday() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String hrToken = bearer("hr1");
        String created = mvc.perform(post("/api/leaves").header("Authorization", bearer("priya"))
                        .contentType(MediaType.APPLICATION_JSON).content(leaveJson(today, today.plusDays(1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/leaves/" + mapper.readTree(created).get("id").asText() + "/approve")
                .header("Authorization", hrToken)).andExpect(status().isOk());

        mvc.perform(get("/api/leaves/on-leave-today").header("Authorization", hrToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-001"));
    }

    @Test
    void staffCannotApproveTheirOwnLeave() throws Exception {
        String hrToken = bearer("hr1");
        String created = mvc.perform(post("/api/leaves").header("Authorization", hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"" + employeeId + "\",\"type\":\"SICK\",\"startDate\":\"" + LocalDate.now(ZoneOffset.UTC).plusDays(3)
                                + "\",\"endDate\":\"" + LocalDate.now(ZoneOffset.UTC).plusDays(3) + "\",\"reason\":\"Filed by HR\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/leaves/" + mapper.readTree(created).get("id").asText() + "/approve").header("Authorization", hrToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsPreflightIsOnlyAllowedForConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/employees").header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk());
        mvc.perform(options("/api/employees").header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
