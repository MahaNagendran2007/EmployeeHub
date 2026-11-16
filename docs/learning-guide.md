# EmployeeHub learning guide and phase runbook

Verification rule used here: an item is "verified" only if a command was actually run and passed. Items marked **YOU RUN** must be executed on your machine; paste the output to your assistant if anything fails.

## 1. Request flow (create employee, end to end)

1. **Browser** – the submit handler in `frontend/js/dialogs.js` (via `api.js`) calls `fetch("<API_BASE_URL>/api/employees", {method:"POST", body: JSON})`.
2. **CORS** – because the page (port 5500) and API (8080) differ, the browser sends an `OPTIONS` preflight; `CorsConfig` allows it only for configured origins.
3. **EmployeeController** – Spring MVC routes `POST /api/employees` here. `@Valid` runs the annotations on `EmployeeRequest`. If invalid, `MethodArgumentNotValidException` is thrown and `GlobalExceptionHandler` returns a 400.
4. **EmployeeService.create** – maps the DTO to an `Employee`, normalises code/email, checks duplicates, sets timestamps, calls the repository.
5. **EmployeeRepository.save** – Spring Data turns the object into a BSON document and inserts it into `employees`. The unique indexes make MongoDB reject duplicates.
6. **Back up the chain** – the saved entity becomes an `EmployeeResponse`, the controller returns 201 + `Location`, and the frontend closes the modal, shows a toast and reloads the table from the API.

## 2. What each major class and annotation does

| Class | Purpose | Key annotations |
|-------|---------|-----------------|
| `EmployeeHubApplication` | Entry point; starts Spring and component scanning | `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` |
| `Employee` | The MongoDB document | `@Document` (collection name), `@Id` (maps `_id`), `@Indexed(unique=true)` (index on startup) |
| `EmploymentStatus` | Allowed status values | – |
| `EmployeeRequest` | Incoming JSON + validation rules | `@NotBlank`, `@NotNull`, `@Email`, `@Pattern`, `@Size`; `@Schema` (Swagger examples) |
| `EmployeeResponse`, `PageResponse`, `EmployeeStatsResponse`, `CountItem` | Outgoing JSON shapes | records |
| `ErrorResponse`, `FieldErrorDetail` | One error format for every failure | records |
| `EmployeeController` | HTTP layer only: routes, status codes, delegates | `@RestController`, `@RequestMapping`, `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`, `@Valid`, `@ResponseStatus`; Swagger `@Operation/@ApiResponse` |
| `EmployeeService` | Business rules: id check, uniqueness, timestamps, page-size clamp, sort whitelist | `@Service`; constructor injection; `@Value` reads `app.pagination.max-page-size` |
| `EmployeeMapper` | DTO ↔ entity, trims/normalises values | `@Component` |
| `EmployeeRepository` | CRUD + `findByEmail`/`findByEmployeeCode` (Spring Data derives the query from the method name) | extends `MongoRepository` |
| `EmployeeSearchRepositoryImpl` | Filtered/paged search and `$group` counts using `MongoTemplate` | picked up automatically because its name is the interface name + `Impl` |
| `EmployeeSearchCriteria` | Carries search/filter values; blanks become `null` | record |
| `GlobalExceptionHandler` | Turns exceptions into `ErrorResponse` | `@RestControllerAdvice`, `@ExceptionHandler` |
| `CorsConfig` | Which browser origins may call `/api/**` | `@Configuration`, `WebMvcConfigurer` |
| `MongoConfig` | Stores `LocalDate` as UTC midnight | `@Bean`, `@ReadingConverter`/`@WritingConverter` |
| `AppConfig` | Provides a `Clock` bean so tests can fix the time | `@Bean` |
| `OpenApiConfig` | Swagger title/version | `@Bean` |
| `DemoDataSeeder` | Optional sample data | `@Profile("demo")`, `CommandLineRunner` |

**Dependency injection:** Spring creates one instance of each `@Service`/`@Component`/`@RestController` and passes them into constructors. That is why `EmployeeController` never writes `new EmployeeService(...)`.

## 3. Phase runbook

### Phase 1 – MongoDB connectivity and CRUD
Status: code written; **not run** (no Maven/MongoDB in the build sandbox).
**YOU RUN:**
```bash
docker compose up -d
cd backend && mvn spring-boot:run          # look for "Started EmployeeHubApplication" and no Mongo errors
curl localhost:8080/actuator/health        # expect {"status":"UP"}
curl -X POST localhost:8080/api/employees -H "Content-Type: application/json" \
  -d '{"employeeCode":"EMP-1","fullName":"Test User","email":"t@example.com","department":"IT","jobTitle":"Dev","location":"Chennai","joiningDate":"2024-01-01","employmentStatus":"ACTIVE"}'
# expect 201 and JSON with an "id"; then GET/PUT/DELETE /api/employees/<id>
```
Persistence check: stop the backend (Ctrl+C), start it again, `GET /api/employees` – the record must still be there. Also look at it in MongoDB Compass (`employeehub` → `employees`).
Understand before continuing: the request flow above, what `@Document`, `@Id` and `@Indexed` do, and why DTOs exist.

### Phase 2 – Frontend connected to the backend
**YOU RUN:** serve `frontend/` on 5500, open it, and tick each item: add, edit, view, delete (with confirm), cancel buttons, validation messages, duplicate-email message, stop backend → offline banner → Retry.
Understand: `request()` in `api.js`, the `state` object in `employees.js`, `ApiError`, and why CORS is needed.

### Phase 3 – Validation, errors, search, filter, pagination
**YOU RUN:** invalid POST (blank name) → 400 with `fieldErrors`; GET bad id → 400; unknown id → 404; duplicate → 409; `GET /api/employees?search=test&size=5&page=0&sortBy=fullName&direction=asc`.
Understand: `GlobalExceptionHandler`, `EmployeeSearchRepositoryImpl.toCriteria`, why page size is clamped.

### Phase 4 – Automated tests, Swagger, README
**YOU RUN:** `cd backend && mvn test`. Expect unit and controller tests to run; the integration test runs only if Docker is running, otherwise it is reported as skipped. Open `/swagger-ui.html` and try each endpoint.
Understand: Mockito mocks vs. real MongoDB (Testcontainers), what `@WebMvcTest` loads.

## 4. If a step fails
Send the exact command and the first error lines. Likely first-run issues: a dependency or API detail in the Java code that only the compiler can catch, or an index-creation error if the database already holds duplicate emails.
