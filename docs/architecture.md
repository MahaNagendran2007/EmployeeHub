# EmployeeHub architecture

## Request flow

```
Browser (fetch) → EmployeeController → EmployeeService → EmployeeRepository → MongoDB
                        ▲                      │
                        └── GlobalExceptionHandler ◄── exceptions
```

1. The browser calls `fetch()` against `API_BASE_URL + /api/employees…`.
2. `EmployeeController` binds the request, runs Jakarta validation (`@Valid`) on bodies, and delegates to the service. It contains no business logic.
3. `EmployeeService` applies the rules: ObjectId validation, normalisation (code upper-case, email lower-case), uniqueness checks, timestamps, page-size clamping and sort-field whitelisting.
4. `EmployeeMapper` converts between `EmployeeRequest`/`EmployeeResponse` DTOs and the `Employee` document, so the API contract is independent of the database model.
5. `EmployeeRepository` (Spring Data) handles basic CRUD and finders. `EmployeeSearchRepositoryImpl` uses `MongoTemplate` for filtered/paged queries and `$group` aggregations, so filtering and counting happen in MongoDB, not in memory.

## Validation

- Boundary validation: annotations on `EmployeeRequest` (required fields, lengths, email and phone patterns, enum and date parsing).
- Business validation: uniqueness of `employeeCode` and `email` (service pre-check + unique indexes), valid ObjectId, allowed sort fields, direction.

## Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to one `ErrorResponse` shape (`timestamp, status, error, message, path, fieldErrors`):

| Situation | HTTP status |
|-----------|-------------|
| Bean Validation failure, malformed JSON, bad enum/date, invalid id, bad sort/direction | 400 |
| Employee not found / unknown route | 404 |
| Duplicate code or email (pre-check or `DuplicateKeyException`) | 409 |
| Wrong HTTP method / content type | 405 / 415 |
| MongoDB unavailable (`DataAccessException`) | 503 |
| Anything else | 500 with a generic message (details only in server logs) |

## Pagination, sorting and filtering

The controller accepts `page`, `size`, `sortBy`, `direction` and optional `search`, `department`, `location`, `status`. The service clamps `page ≥ 0` and `1 ≤ size ≤ 100`, whitelists `sortBy`, and adds `_id` as a tiebreaker so page boundaries are stable. The repository builds one MongoDB `Criteria`, counts matches, then fetches only the requested page. Search text is quoted with `Pattern.quote` so it is matched literally.

## Frontend state

The frontend is split into ES modules: `api.js` (all fetch calls, timeout, `ApiError`), `ui.js` (escaping, toasts, avatars), `lookups.js`, `charts.js`, `dashboard.js`, `employees.js`, `dialogs.js` (form/details/delete) and `main.js` (hash router and shell). `employees.js` keeps a small `state` object (page, size, sort, filters, rows). Any change to filters, sort or page updates `state` and calls `loadEmployees()`, which fetches the page, re-renders table, empty state and pagination. A request counter discards out-of-date responses. After create/update/delete the current view reloads, so the UI always reflects what MongoDB holds; nothing is cached in `localStorage`. Server validation errors (`fieldErrors`) are applied to inputs with `setCustomValidity`, which makes Bootstrap show the server message beside the field. Network failures produce the offline banner with a Retry button.

## Manual verification checklist

Run after `mvn test` passes, with MongoDB and both servers running:

- [ ] Dashboard shows 0 employees on an empty database and an empty-state message
- [ ] Add an employee; it appears in the Employees table and totals update
- [ ] Add another with the same email → error shown under the Email field
- [ ] Edit an employee, view its details, then delete it after the confirmation dialog
- [ ] Search by name, code and email; filter by department, location and status
- [ ] With 11+ employees, page through results and change rows per page
- [ ] Refresh the browser and restart the backend: records persist
- [ ] Stop the backend: offline banner appears; restart and click Retry
- [ ] Open `/swagger-ui/index.html` and `/actuator/health`

## Stage 1 additions

**Authentication flow:** `POST /api/auth/login` → `AuthService` checks the BCrypt hash (and the 5-failure lockout) → `JwtService` signs an HS256 token with the role in a `roles` claim → the browser keeps it in `sessionStorage` and sends `Authorization: Bearer …` on every call (`frontend/js/api.js`). Spring Security's resource-server filter validates the token; controllers add `@PreAuthorize` rules; `ActorResolver` reloads the account from MongoDB so a disabled user is refused even with a valid token. A 401 clears the session and the page reloads to the sign-in screen (no data of the previous user stays in the DOM).

**Leave flow:** `LeaveController` → `LeaveService` (date rules, overlap query, no self-approval) → `LeaveRepository`; each step also writes an `ActivityLog` entry and an in-app `Notification` (roles or a specific user).

**CSV:** `EmployeeController` `/export` → `EmployeeService.exportCsv` → `EmployeeSearchRepositoryImpl.findAllMatching` → `EmployeeCsv.write` (formula-injection safe). `/import` → `EmployeeImportService` parses, validates every row with Bean Validation, and reuses `EmployeeService.create` so duplicate rules are identical to the form.

**Trends:** MongoDB aggregation (`$project` year/month → `$group`) in `EmployeeSearchRepositoryImpl.countJoinedByMonth`; `EmployeeService.joiningTrend` zero-fills the months.

## Stage 1 manual checklist (run after `mvn test`)

- [ ] Start with admin env vars; sign in; wrong password shows an error
- [ ] Create an HR user and an EMPLOYEE user (linked to an existing code); sign in as each
- [ ] EMPLOYEE sees only My Profile and Leave; opening `/api/employees` with their token returns 403 in Swagger
- [ ] EMPLOYEE applies for leave; overlapping dates are refused; HR gets a bell notification, approves, employee is notified
- [ ] Approved leave covering today appears under "On leave today"
- [ ] HR cannot delete employees (403); ADMIN can
- [ ] Export with a filter; import a file with one bad row and one duplicate and read the summary
- [ ] Reports: download CSV, Print / Save as PDF
- [ ] Activity log shows the events above
