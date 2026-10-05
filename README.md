# Event Tickets API

Spring Boot 4.1 · Java 25 · PostgreSQL · Flyway · Spring Security (JWT)

📚 Full documentation is in [`docs/`](docs/README.md): [architecture](docs/architecture.md) · [business rules](docs/business-rules.md) · [API](docs/api.md) · [database](docs/database.md) · [security](docs/security.md) · [development](docs/development.md) · [testing with curl](docs/testing-with-curl.md)

## Business rules

| # | Rule | How it's enforced |
|---|------|-------------------|
| 01 | No overbooking: asking for more tickets than seats left → **409** | `Event.reserveSeats()` under a `SELECT … FOR UPDATE` row lock (`EventRepository.findByIdForUpdate`), plus a DB `CHECK (available_seats BETWEEN 0 AND total_seats)` |
| 02 | Seats stay correct: booking takes seats, cancelling gives them back | `BookingService.create` / `cancel`; booking row locked so it can't be cancelled twice (2nd cancel → **409**) |
| 03 | Max 4 tickets per booking: quantity outside 1–4 → **400** | `@Min(1) @Max(4)` on `BookingRequest.quantity`, plus a DB `CHECK` |
| 04 | Price comes from the server | `BookingRequest` has no price field; `Booking` copies `event.price` and computes `price × quantity` |
| 05 | Event is CANCELLED → **409** | `Event.reserveSeats()` |
| 06 | Customers can view/cancel only their own bookings → **403** | `BookingService.checkAccess()` (admins can see all) |

Errors are returned as RFC 9457 `application/problem+json`.

## Project structure

```
src/main/java/com/devtraining/tickets
├── EventTicketsApplication.java
├── config/            SecurityConfig (JWT, roles), JwtProperties, AdminProperties, AdminAccountInitializer
├── common/exception/  ApiException → NotFound(404) / Conflict(409) / Forbidden(403), GlobalExceptionHandler
├── auth/              AuthController, AuthService, JwtService, CurrentUser, dto/
├── user/              User entity, Role, UserRepository, UserResponse
├── event/             Event entity, EventStatus, EventRepository, EventService, EventController, dto/
└── booking/           Booking entity, BookingStatus, BookingRepository, BookingService, BookingController, dto/
src/main/resources
├── application.yml
└── db/migration/V1__init_schema.sql   (Flyway; Hibernate only validates the schema)
```

Each feature package follows **Controller → Service → Repository → Entity**. Controllers use DTO records only and never return entities. Business rules live in the entities and services.

## Running locally

1. Start PostgreSQL. Either:
   - `docker compose up -d` (also creates the `event_tickets_test` database), or
   - your own PostgreSQL: `CREATE DATABASE event_tickets;` and `CREATE DATABASE event_tickets_test;`
2. Set the DB credentials if they aren't `postgres/postgres`:
   ```powershell
   $env:DB_USERNAME="postgres"; $env:DB_PASSWORD="your-password"
   ```
3. Run:
   ```powershell
   ./mvnw spring-boot:run
   ```

On startup, Flyway creates the tables and an admin account is created: `admin@tickets.local` / `Admin@12345`. Override it with `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

| Env var | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/event_tickets` |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` |
| `JWT_SECRET` | dev-only value. **Set a random secret of 32+ characters in production** |
| `JWT_EXPIRATION` | `1h` |
| `PORT` | `8080` |

## API

| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/register` | public (always creates a CUSTOMER) |
| POST | `/api/auth/login` | public → returns `accessToken` |
| GET | `/api/auth/me` | authenticated |
| GET | `/api/events`, `/api/events/{id}` | public |
| POST | `/api/events` | ADMIN |
| PUT | `/api/events/{id}` | ADMIN |
| POST | `/api/events/{id}/cancel` | ADMIN |
| POST | `/api/bookings` | authenticated `{ "eventId": 1, "quantity": 2 }` |
| GET | `/api/bookings` | own bookings (admin: all) |
| GET | `/api/bookings/{id}` | owner or admin |
| POST | `/api/bookings/{id}/cancel` | owner or admin |

Send the token as `Authorization: Bearer <accessToken>`. Ready-made requests are in [`api.http`](api.http).

## Tests

```powershell
./mvnw test      # unit tests (BookingServiceTest), no database needed
./mvnw verify    # + BookingApiIT: end-to-end and concurrency tests against event_tickets_test
```
