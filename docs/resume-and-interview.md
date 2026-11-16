# Resume entry and interview preparation

## Resume entry

> **Use this only after `mvn test` passes locally and you have clicked through the manual checklist in `docs/architecture.md`.** Delete any bullet you did not personally verify. Add your real GitHub link only after you publish.

**EmployeeHub — Full-Stack Employee Management System** | Java 21, Spring Boot 3, Spring Data MongoDB, REST, Bootstrap 5, JavaScript, JUnit 5, Mockito, Testcontainers

- Built a layered Spring Boot REST API (controller, service, repository, DTO, mapper) with MongoDB persistence for employee CRUD, server-side search, filtering, sorting and pagination using `MongoTemplate` queries and aggregation pipelines.
- Implemented Jakarta Bean Validation, unique MongoDB indexes with duplicate-key handling, and a global `@RestControllerAdvice` that returns one consistent JSON error format; documented the API with Swagger UI and a Postman collection.
- Developed a responsive vanilla-JavaScript/Bootstrap admin dashboard integrated through the Fetch API, and wrote JUnit 5/Mockito unit tests, MockMvc controller tests and Testcontainers integration tests.

## 15 interview questions

**1. What does Spring Boot give you over plain Spring?**
Auto-configuration, starter dependencies, an embedded Tomcat and sensible defaults, so you can run a web app with one `main` method and minimal XML/Java config.

**2. Explain the Spring MVC request flow in this project.**
`DispatcherServlet` receives the request, `HandlerMapping` finds `EmployeeController`, argument resolvers bind the path/query/body (running `@Valid`), the method returns an object, and `HttpMessageConverters` (Jackson) write JSON. Exceptions go to `GlobalExceptionHandler`.

**3. Which HTTP methods and status codes do you use, and why?**
POST create → 201 + `Location`; GET read → 200; PUT full update → 200; DELETE → 204; validation → 400; missing → 404; duplicate → 409. PUT replaces all editable fields and is idempotent; POST is not.

**4. What is dependency injection and how do you use it?**
Objects receive their dependencies instead of creating them. I use constructor injection (`EmployeeController(EmployeeService)`), which makes dependencies explicit, fields `final`, and classes easy to unit-test by passing mocks.

**5. How does Spring Data MongoDB work here?**
`EmployeeRepository extends MongoRepository` gives CRUD and derived finders like `findByEmail`. For paged filtering and `$group` counts I added a custom fragment (`EmployeeSearchRepositoryImpl`) using `MongoTemplate`, which Spring Data merges into the repository.

**6. Why separate controller, service and repository layers?**
Controllers handle HTTP, services hold business rules (uniqueness, normalisation, limits), repositories handle persistence. Each layer is testable alone and changes stay local.

**7. Why use DTOs instead of returning the entity?**
They decouple the API contract from the database model, prevent leaking internal fields, let request and response shapes differ, and give a place for validation annotations.

**8. How does validation work?**
Jakarta annotations (`@NotBlank`, `@Email`, `@Pattern`, `@Size`, `@NotNull`) on `EmployeeRequest`, triggered by `@Valid`. Failures raise `MethodArgumentNotValidException`, which my handler converts into a 400 with `fieldErrors`. Business rules such as uniqueness are checked in the service.

**9. How do you handle exceptions centrally?**
A `@RestControllerAdvice` with `@ExceptionHandler` methods maps each exception type to an HTTP status and a single `ErrorResponse` structure; unexpected errors return a generic 500 and are logged server-side only.

**10. How are pagination and filtering implemented?**
The controller passes `page`, `size`, `sortBy`, `direction` and filters; the service clamps page size to 100 and whitelists sort fields; the repository builds a MongoDB `Criteria`, counts matches and fetches one page with skip/limit — nothing is filtered in memory. An `_id` tiebreaker keeps ordering stable.

**11. How do you enforce unique emails with MongoDB?**
`@Indexed(unique = true)` plus `auto-index-creation=true` creates a unique index. The service pre-checks for friendly errors, and a `DuplicateKeyException` from a race condition is mapped to 409. Index creation fails if existing data already has duplicates.

**12. How did you test the application?**
Mockito unit tests for the service (mocked repository), `@WebMvcTest` with MockMvc for status codes, validation and error format, and Testcontainers integration tests against a real MongoDB container so the developer's database is never touched.

**13. What is the difference between a unit test and an integration test?**
A unit test isolates one class with mocked collaborators and runs in milliseconds; an integration test wires real components (Spring context, real MongoDB) to verify they work together, such as unique indexes and aggregations.

**14. What is CORS and how did you configure it?**
Browsers block cross-origin requests unless the server allows the origin. The frontend runs on port 5500 and the API on 8080, so `CorsConfig` allows only the origins in `CORS_ALLOWED_ORIGINS` for `/api/**`, with specific methods and headers instead of a wildcard.

**15. How do you manage configuration and document the API?**
Environment variables (`MONGODB_URI`, `PORT`, `CORS_ALLOWED_ORIGINS`) feed `application.properties`, so no secrets are committed. springdoc-openapi generates Swagger UI from controller annotations, and a Postman collection provides runnable examples.

## Short architecture summary for interviews

- `Employee` is the MongoDB document; `EmployeeRequest`/`EmployeeResponse` are the API contract.
- `EmployeeService` is where business rules live; it depends on `EmployeeRepository`, `EmployeeMapper` and an injected `Clock` (deterministic tests).
- `EmployeeSearchRepositoryImpl` runs filtered/paged queries and aggregations inside MongoDB.
- `GlobalExceptionHandler` guarantees one error shape; `CorsConfig` restricts browser access; `MongoConfig` stores dates as UTC to avoid time-zone shifts.
- The frontend is one static page: a `state` object drives `fetch` calls and re-rendering.

## Stage 1 additions (add only after `mvn test` passes and you have tried each feature)

- Added role-based security (ADMIN/HR/EMPLOYEE) with Spring Security, stateless JWT and BCrypt, enforced with method-level `@PreAuthorize`.
- Built a leave-management module (request, overlap detection, approval workflow, audit log, in-app notifications) persisted in MongoDB.
- Implemented CSV import with per-row validation and CSV export with formula-injection protection; MongoDB aggregation for monthly joining trends.

Extra interview questions: *Why JWT instead of sessions?* (stateless API, no server session store; trade-off: tokens can't be revoked before expiry and live in browser storage). *Why is CSRF disabled?* (no cookies are used for auth, so browsers do not attach credentials automatically). *How do you prevent users approving their own leave?* (service-level check on requester and linked employee, covered by unit and integration tests). *What is CSV formula injection?* (cells starting with `=`, `+`, `-`, `@` can execute in spreadsheets; export prefixes them with `'`).
