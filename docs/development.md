# Development guide

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 25 |
| PostgreSQL | 15+ (developed against 18) **or** Docker |
| Maven | not required: use the wrapper `./mvnw` (macOS/Linux) or `mvnw.cmd` (Windows) |

## 1. Database

**Option A: Docker**

```bash
docker compose up -d
```

This starts PostgreSQL 18 on port 5432 (user/password `postgres`/`postgres`) and creates the `event_tickets` and `event_tickets_test` databases.

**Option B: local PostgreSQL**

```sql
CREATE DATABASE event_tickets;
CREATE DATABASE event_tickets_test;
```

You don't create any tables yourself. Flyway creates them on startup.

## 2. Configuration

All settings are in `src/main/resources/application.yml` and can be overridden with environment variables:

| Env var | Property | Default |
|---|---|---|
| `DB_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/event_tickets` |
| `DB_USERNAME` | `spring.datasource.username` | `postgres` |
| `DB_PASSWORD` | `spring.datasource.password` | `postgres` |
| `JWT_SECRET` | `app.jwt.secret` | dev-only value (must be ≥ 32 chars) |
| `JWT_EXPIRATION` | `app.jwt.expiration` | `1h` (any Spring duration: `30m`, `2h`, ...) |
| `ADMIN_EMAIL` | `app.admin.email` | `admin@tickets.local` |
| `ADMIN_PASSWORD` | `app.admin.password` | `Admin@12345` |
| `PORT` | `server.port` | `8080` |
| `TEST_DB_URL` | test profile datasource | `jdbc:postgresql://localhost:5432/event_tickets_test` |

The `app.*` properties are validated at startup (`JwtProperties`, `AdminProperties`). For example, the app refuses to start if the JWT secret is too short.

PowerShell:

```powershell
$env:DB_PASSWORD = "your-password"
```

bash:

```bash
export DB_PASSWORD=your-password
```

## 3. Run

```bash
./mvnw spring-boot:run
```

Check that it's up:

```bash
curl http://localhost:8080/actuator/health      # {"status":"UP"}
```

Then try the requests in [`api.http`](../api.http). Log in as the admin first, create an event, then register a customer and book tickets.

Build a runnable jar:

```bash
./mvnw clean package
java -jar target/event-tickets-0.0.1-SNAPSHOT.jar
```

## 4. Tests

| Command | Runs | Needs DB |
|---|---|---|
| `./mvnw test` | unit tests (`*Test`) via Surefire | No |
| `./mvnw verify` | unit tests, plus integration tests (`*IT`) via Failsafe | Yes: `event_tickets_test` |

| Test class | Type | Covers |
|---|---|---|
| `booking/BookingServiceTest` | Unit (JUnit 5 + Mockito) | rules 01, 02, 04, 05, 06 at service level |
| `booking/BookingApiIT` | Integration (`@SpringBootTest` + MockMvc + real PostgreSQL) | all six rules over HTTP, auth (401/403/409), and 20 concurrent bookings for 10 seats |

`BookingApiIT` deletes all bookings, events and non-admin users in `event_tickets_test` before each test. **Never point `TEST_DB_URL` at a database with real data.**

## Coding conventions

- **Package by feature:** `auth`, `user`, `event`, `booking`. Shared code goes in `common` or `config`.
- **Layering:** Controller → Service → Repository / Entity. See [architecture.md](architecture.md#layers).
- **DTOs are records** in a `dto` sub-package, with a static `from(entity)` factory on response DTOs. Never return entities from controllers.
- **Validation** with Jakarta annotations on request records, and `@Valid` on the controller parameter.
- **Errors:** throw `NotFoundException`, `ConflictException` or `ForbiddenException` (or a new `ApiException` subclass). Don't build error responses in controllers.
- **Transactions:** put `@Transactional` on service methods. Use `readOnly = true` for queries.
- **Invariants live in entities:** use methods like `reserveSeats` and `cancel`, not public setters.
- **Constructor injection only**, with `final` fields. No field `@Autowired`.
- **Schema changes go through Flyway:** add a new `V{n}__description.sql` and never edit an applied migration.
- **Style:** follows the Spring Boot code style: tabs for indentation, and lambda parameters in parentheses `(x) -> ...`.

## Adding a feature (checklist)

1. Migration: `db/migration/V{n}__add_xxx.sql`
2. Entity and enum(s) in a new package; put the invariants in entity methods
3. Repository interface (`JpaRepository`), with locking queries if the data is contended
4. Request/response records in `dto/`
5. Service with `@Transactional` methods that take `CurrentUser` when access depends on the caller
6. Controller
7. URL authorization rules in `SecurityConfig` if the new paths aren't covered by "authenticated"
8. Unit tests for the service and an `*IT` test for the HTTP contract
9. Update `docs/api.md` and `docs/business-rules.md`

## Troubleshooting

| Symptom | Fix |
|---|---|
| `password authentication failed for user "postgres"` | Set `DB_USERNAME` / `DB_PASSWORD` |
| `database "event_tickets" does not exist` | Create it (see step 1) |
| `Schema-validation: missing table ...` | Flyway didn't run, or the entity and migration differ. Check the `flyway_schema_history` table |
| `Validation failed for checksum` (Flyway) | An applied migration was edited. Revert it, and add a new migration instead |
| App fails on startup: `app.jwt.secret ... at least 32 characters` | Make `JWT_SECRET` longer |
| `401` with a token that worked before | Token expired, or `JWT_SECRET` changed: log in again |
