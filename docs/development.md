# Development guide

## Prerequisites

There are two ways to work on the project. Pick one.

| Path | You need | Good for |
|---|---|---|
| **A. Docker only** | Docker (Desktop or Engine) with Compose | trying the API, running the tests, no Java install |
| **B. Local JDK** | JDK 25, plus PostgreSQL 15+ (developed against 18) **or** Docker for the database | day-to-day development in an IDE |

Maven is never required: use the wrapper `./mvnw` (macOS/Linux) or `mvnw.cmd` (Windows). `mvnw` is committed as executable; if your checkout lost the flag, run `chmod +x mvnw` or call it as `sh ./mvnw`.

## Path A: Docker only (no JDK)

```bash
docker compose up -d --build     # builds the jar with JDK 25 inside Docker, starts PostgreSQL + API
docker compose logs -f app       # follow the API logs
docker compose down              # stop (add -v to also delete the database volume)
```

The API listens on **http://localhost:8090** and PostgreSQL on **localhost:5434**.

The `Dockerfile` is a multi-stage build: a JDK 25 stage compiles the jar (`./mvnw package -DskipTests`, with a cached Maven repository), and a JRE 25 stage runs it as a non-root user (`app`, UID 1001).

Compose passes `BOOKING_HOLD_DURATION` and `BOOKING_CANCEL_CUTOFF` through from your shell, which helps when testing by hand. For example, to make holds expire after one minute:

```bash
BOOKING_HOLD_DURATION=1m docker compose up -d                  # macOS / Linux
```

```powershell
$env:BOOKING_HOLD_DURATION = "1m"; docker compose up -d       # Windows PowerShell
```

Other settings (see the table below) can be added to the `environment:` section of the `app` service.

Run the tests in a JDK 25 container (PostgreSQL from compose must be up):

```bash
# macOS / Linux
docker run --rm --network dev_training_backend_default -v "$PWD":/workspace -v m2:/root/.m2 -w /workspace \
  -e TEST_DB_URL=jdbc:postgresql://postgres:5432/event_tickets_test eclipse-temurin:25-jdk sh ./mvnw -B verify
```

```powershell
# Windows PowerShell
docker run --rm --network dev_training_backend_default -v "${PWD}:/workspace" -v m2:/root/.m2 -w /workspace `
  -e TEST_DB_URL=jdbc:postgresql://postgres:5432/event_tickets_test eclipse-temurin:25-jdk sh ./mvnw -B verify
```

The network name is `<folder name>_default`. If you cloned into a different folder, check it with `docker network ls`.

## Path B: local JDK 25

### 1. Database

**Option 1: Docker**

```bash
docker compose up -d postgres
```

This starts PostgreSQL 18 on host port 5434 (user/password `postgres`/`postgres`) and creates the `event_tickets` and `event_tickets_test` databases (`docker/init-test-db.sql`).

**Option 2: local PostgreSQL**

```sql
CREATE DATABASE event_tickets;
CREATE DATABASE event_tickets_test;
```

Then point `DB_URL` / `TEST_DB_URL` at your server (the defaults use port 5434). The migration creates the `btree_gist` extension; it is a trusted extension, so the database owner can create it.

You don't create any tables yourself. Flyway creates them on startup.

### 2. Configuration

All settings are in `src/main/resources/application.yml` and can be overridden with environment variables:

| Env var | Property | Default |
|---|---|---|
| `DB_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5434/event_tickets` |
| `DB_USERNAME` | `spring.datasource.username` | `postgres` |
| `DB_PASSWORD` | `spring.datasource.password` | `postgres` |
| `JWT_SECRET` | `app.jwt.secret` | dev-only value (must be ≥ 32 chars) |
| `JWT_EXPIRATION` | `app.jwt.expiration` | `1h` (any Spring duration: `30m`, `2h`, ...) |
| `ADMIN_EMAIL` | `app.admin.email` | `admin@tickets.local` |
| `ADMIN_PASSWORD` | `app.admin.password` | `Admin@12345` |
| `PORT` | `server.port` | `8090` |
| `BOOKING_MAX_TICKETS_PER_CUSTOMER` | `app.booking.max-tickets-per-customer` | `8` (seats one customer can hold or own per showtime, across all their bookings) |
| `BOOKING_CANCEL_CUTOFF` | `app.booking.cancel-cutoff` | `2h` (customers can't cancel a paid booking closer to the start; admins can) |
| `BOOKING_HOLD_DURATION` | `app.booking.hold-duration` | `10m` (how long a new booking holds its seats before it must be paid) |
| `BOOKING_PREVENT_SINGLE_SEAT_GAPS` | `app.booking.prevent-single-seat-gaps` | `true` (reject choices that leave one empty seat between taken seats) |
| `BOOKING_EXPIRY_SWEEP_INTERVAL` | `app.booking.expiry-sweep-interval` | `PT1M` (how often `HoldExpiryJob` runs; ISO-8601 duration) |
| `CINEMA_TIME_ZONE` | `app.cinema.time-zone` | `Asia/Phnom_Penh` (local date used for age checks) |
| `CINEMA_CLEANING_TIME` | `app.cinema.cleaning-time` | `15m` (hall blocked after each movie; added to `ends_at`) |
| `CINEMA_CHECK_IN_OPENS_BEFORE` | `app.cinema.check-in-opens-before` | `1h` (how early before the start tickets can be checked in) |
| `TEST_DB_URL` | test profile datasource | `jdbc:postgresql://localhost:5434/event_tickets_test` |

The `app.*` properties are validated at startup (`JwtProperties`, `AdminProperties`, `BookingProperties`, `CinemaProperties`). For example, the app refuses to start if the JWT secret is too short or the time zone is unknown.

Setting a variable for the current shell:

```bash
export DB_PASSWORD=your-password                 # macOS / Linux (bash, zsh)
```

```powershell
$env:DB_PASSWORD = "your-password"               # Windows PowerShell
```

### 3. Run

```bash
./mvnw spring-boot:run                           # macOS / Linux / Git Bash
```

```powershell
.\mvnw.cmd spring-boot:run                       # Windows
```

Check that it's up:

```bash
curl http://localhost:8090/actuator/health                     # {"status":"UP"}
```

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health        # status: UP
```

Build a runnable jar:

```bash
./mvnw clean package
java -jar target/event-tickets-0.0.1-SNAPSHOT.jar
```

```powershell
.\mvnw.cmd clean package
java -jar target\event-tickets-0.0.1-SNAPSHOT.jar
```

## Trying the API by hand

On startup an admin account is created: `admin@tickets.local` / `Admin@12345`. A typical walk-through is: admin creates a movie, a hall and a showtime → a customer registers, picks seats from the seat map, holds them and pays → admin checks the ticket in.

| Guide | For |
|---|---|
| [testing-with-curl.md](testing-with-curl.md) | macOS / Linux terminal (curl + jq) |
| [testing-with-powershell.md](testing-with-powershell.md) | Windows PowerShell |
| [testing-with-postman.md](testing-with-postman.md) | Postman |

Ready-made requests are also in [`api.http`](../api.http) (IntelliJ / VS Code REST Client). The endpoints are listed in [api.md](api.md).

## Tests

| Command | Runs | Needs DB |
|---|---|---|
| `./mvnw test` | 22 unit tests (`*Test`) via Surefire | No |
| `./mvnw verify` | the unit tests, plus 14 integration tests (`*IT`) via Failsafe | Yes: `event_tickets_test` |

On Windows use `.\mvnw.cmd test` / `.\mvnw.cmd verify`.

| Test class | Type | Covers |
|---|---|---|
| `booking/BookingServiceTest` (18) | Unit (JUnit 5 + Mockito, fixed `Clock`) | holding seats with server prices, expiring old holds first, taken / unknown / duplicate seats, single-seat gaps (and turning the rule off), per-customer limit, sales closed, age rating, idempotency replay, pay and expired holds, ownership, cancel and cancel deadline (customer vs admin), check-in window |
| `showtime/ShowtimeTest` (4) | Unit (plain JUnit 5) | seat price by seat type, sales close at the start, cancelled showtime not for sale, start time can't move once seats are sold |
| `booking/BookingApiIT` (14) | Integration (`@SpringBootTest` + MockMvc + real PostgreSQL) | the full flow over HTTP, hold expiry and the expiry job, idempotency, limits, seat gaps, age rating, sale / cancel / check-in windows, overlapping showtimes, showtime cancel cascade, security rules (401/403), 20 concurrent customers for the same 2 seats (exactly 1 wins), and the no-deadlock test |

Time-based rules are tested without waiting. Services read time from the `Clock` bean (`ClockConfig`). `BookingServiceTest` builds the service with a fixed clock. `BookingApiIT` imports `support/TestClockConfig`, which registers a `@Primary` `support/MutableClock`, and the tests move it forward to expire holds or reach the showtime start.

`BookingApiIT` deletes all booking seats, bookings, showtimes, seats, halls, movies and non-admin users in `event_tickets_test` before each test. **Never point `TEST_DB_URL` at a database with real data.**

## Coding conventions

- **Package by feature:** `auth`, `user`, `movie`, `hall`, `showtime`, `booking`. Shared code goes in `common` or `config`.
- **Layering:** Controller → Service → Repository / Entity. See [architecture.md](architecture.md#layers).
- **DTOs are records** in a `dto` sub-package, with a static `from(entity)` factory on response DTOs. Never return entities from controllers.
- **Validation** with Jakarta annotations on request records, and `@Valid` on the controller parameter.
- **Errors:** throw `BadRequestException`, `ForbiddenException`, `NotFoundException` or `ConflictException` (or a new `ApiException` subclass). Don't build error responses in controllers.
- **Transactions:** put `@Transactional` on service methods. Use `readOnly = true` for queries.
- **Invariants live in entities:** use methods like `Booking.pay`, `Booking.cancel` and `Showtime.checkOpenForSale`, not public setters.
- **Time comes from the `Clock` bean.** Never call `Instant.now()` in a business rule; inject `Clock` and use `clock.instant()`, so the rule can be tested.
- **Lock order:** any write that changes a showtime's seats or bookings locks the showtime row first (`ShowtimeRepository.findByIdForUpdate`), then booking rows. See [database.md](database.md#concurrency-and-locking).
- **Constructor injection only**, with `final` fields. No field `@Autowired`.
- **Schema changes go through Flyway:** add a new `V{n}__description.sql` and never edit an applied migration.
- **Style:** follows the Spring Boot code style: tabs for indentation, and lambda parameters in parentheses `(x) -> ...`.

## Adding a feature (checklist)

1. Migration: `db/migration/V{n}__add_xxx.sql`
2. Entity and enum(s) in a new package; put the invariants in entity methods
3. Repository interface (`JpaRepository`), with locking queries if the data is contended
4. Request/response records in `dto/`
5. Service with `@Transactional` methods that take `CurrentUser` when access depends on the caller, and `Clock` when rules depend on time
6. Controller
7. URL authorization rules in `SecurityConfig` if the new paths aren't covered by "authenticated"
8. Unit tests for the service and an `*IT` test for the HTTP contract
9. Update `docs/api.md` and `docs/business-rules.md`

## Troubleshooting

| Symptom | Fix |
|---|---|
| `password authentication failed for user "postgres"` | Set `DB_USERNAME` / `DB_PASSWORD` |
| `database "event_tickets" does not exist` | Create it (see Path B, step 1) |
| `Connection refused` on port 5434 | Start the database: `docker compose up -d postgres` |
| `permission denied: ./mvnw` | `chmod +x mvnw`, or run `sh ./mvnw ...` |
| `Schema-validation: missing table ...` | Flyway didn't run, or the entity and migration differ. Check the `flyway_schema_history` table |
| `permission denied to create extension "btree_gist"` | Run the migration as the database owner, or ask the DBA to create the extension |
| `Validation failed for checksum` (Flyway) | An applied migration was edited. Revert it, and add a new migration instead |
| App fails on startup: `app.jwt.secret ... at least 32 characters` | Make `JWT_SECRET` longer |
| `401` with a token that worked before | Token expired, or `JWT_SECRET` changed: log in again |
| `docker run ... network dev_training_backend_default not found` | Start compose first, and check the network name with `docker network ls` |
