# Cinema Tickets API

Spring Boot 4.1 · Java 25 · PostgreSQL · Flyway · Spring Security (JWT)

A REST API for booking cinema seats. Admins add movies and halls and schedule showtimes. Customers pick seats on a seat map, hold them for 10 minutes, pay to get a ticket code, and show the code at the door.

📚 Full documentation is in [`docs/`](docs/README.md): [requirements](docs/requirements.md) · [architecture](docs/architecture.md) · [business rules](docs/business-rules.md) · [API](docs/api.md) · [database](docs/database.md) · [security](docs/security.md) · [development](docs/development.md) · [testing with PowerShell](docs/testing-with-powershell.md) · [testing with curl](docs/testing-with-curl.md) · [testing with Postman](docs/testing-with-postman.md)

## Business rules

| # | Rule | How it's enforced |
|---|------|-------------------|
| 01 | A seat is sold only once per showtime: a taken seat → **409** | `BookingService.create` checks taken seats under a `SELECT … FOR UPDATE` lock on the showtime row (`ShowtimeRepository.findByIdForUpdate`), plus a DB unique index `ux_booking_seats_active (showtime_id, seat_id) WHERE active` |
| 02 | Seats are released exactly once: cancelling frees them, a 2nd cancel → **409** | `Booking.cancel()` sets every `BookingSeat` inactive; showtime and booking rows are locked first |
| 03 | 1–4 seats per booking; empty, more than 4, duplicate or unknown seats → **400** | `@Size(min = 1, max = 4)` on `BookingRequest.seats`, `BookingService.resolveSeats()`, plus a DB `CHECK` |
| 04 | Prices come from the server: `basePrice` × STANDARD 1.00 / VIP 1.50 / COUPLE 2.00 | `BookingRequest` has no price field; `Booking` prices each seat with `SeatType.priceFor()` and stores it on `booking_seats` |
| 05 | No booking a cancelled showtime → **409** | `Showtime.checkOpenForSale()` |
| 06 | Customers can view, pay and cancel only their own bookings → **403** | `BookingService.checkAccess()` (admins can see all) |
| 07 | Max 8 seats per customer per showtime, across all held and paid bookings → **409** | `BookingService.checkCustomerLimit()` under the showtime row lock |
| 08 | Sales close when the showtime starts → **409**; showtimes must start in the future → **400** | `Showtime.checkOpenForSale()`, `@Future` on `ShowtimeRequest.startsAt` |
| 09 | Customers can't cancel a paid booking within 2 hours of the start → **409** (admins can) | `BookingService.checkCancelDeadline()` |
| 10 | Cancelling a showtime cancels all its held and paid bookings | `ShowtimeService.cancel()` → `BookingService.cancelAllForShowtime()` |
| 11 | A booking holds its seats for 10 minutes; paying returns a ticket code; an expired hold can't be paid → **409** | `Booking.pay()`, `TicketCodeGenerator`, `HoldExpiryJob` (sweeps every minute) |
| 12 | A retried request with the same `Idempotency-Key` returns the first booking | `BookingService.create()` looks up the key; DB unique `(user_id, idempotency_key)` |
| 13 | No single empty seat left between taken seats in a row → **409** | `BookingService.checkNoSingleSeatGaps()` |
| 14 | PG13 / R18 movies need a date of birth and a high enough age, in Asia/Phnom_Penh on the show day → **403** | `BookingService.checkAge()` |
| 15 | A ticket is checked in once, from 1 hour before the start until the showtime ends → **409** otherwise | `BookingService.checkIn()`, `Booking.checkIn()` |
| 16 | No overlapping showtimes in a hall (movie length + 15 min cleaning) → **409** | `ShowtimeService.checkHallFree()`, plus a PostgreSQL exclusion constraint `ex_showtimes_hall_overlap` |
| 17 | A showtime with sales can't change its movie, hall or start time → **409** (the price can change) | `Showtime.update()` |

Each rule is described in detail in [business rules](docs/business-rules.md), and tested step by step in [testing with curl](docs/testing-with-curl.md).

Errors are returned as RFC 9457 `application/problem+json`.

## Project structure

```
src/main/java/com/devtraining/tickets
├── EventTicketsApplication.java
├── config/            SecurityConfig (JWT, roles), JwtProperties, AdminProperties, BookingProperties,
│                      CinemaProperties, ClockConfig, SchedulingConfig, AdminAccountInitializer
├── common/exception/  ApiException → BadRequest(400) / Forbidden(403) / NotFound(404) / Conflict(409),
│                      GlobalExceptionHandler
├── auth/              AuthController, AuthService, JwtService, CurrentUser, dto/
├── user/              User entity, Role, UserRepository, UserResponse
├── movie/             Movie entity, AgeRating, MovieRepository, MovieService, MovieController, dto/
├── hall/              Hall and Seat entities, SeatType, repositories, HallService, HallController, dto/
├── showtime/          Showtime entity, ShowtimeStatus, ShowtimeRepository, ShowtimeService, ShowtimeController, dto/
└── booking/           Booking and BookingSeat entities, BookingStatus, repositories, BookingService,
                       BookingController, TicketController, HoldExpiryJob, TicketCodeGenerator, dto/
src/main/resources
├── application.yml
└── db/migration/      (Flyway; Hibernate only validates the schema)
    └── V1__init_schema.sql   (the whole cinema schema)
```

Each feature package follows **Controller → Service → Repository → Entity**. Controllers use DTO records only and never return entities. Business rules live in the entities and services.

## Running with Docker only (no JDK needed)

```sh
docker compose up -d --build     # builds the jar with JDK 25 in Docker, starts PostgreSQL + API on :8090
docker compose logs -f app       # follow API logs
```

Run the tests in a JDK 25 container (PostgreSQL must be up):

```sh
docker run --rm --network dev_training_backend_default -v "$PWD":/workspace -v m2:/root/.m2 -w /workspace \
  -e TEST_DB_URL=jdbc:postgresql://postgres:5432/event_tickets_test eclipse-temurin:25-jdk sh ./mvnw -B verify
```

## Running locally

1. Start PostgreSQL. Either:
   - `docker compose up -d postgres` (listens on host port 5434 and also creates the `event_tickets_test` database), or
   - your own PostgreSQL: `CREATE DATABASE event_tickets;` and `CREATE DATABASE event_tickets_test;` (the `btree_gist` extension must be available; it ships with PostgreSQL)
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
| `DB_URL` | `jdbc:postgresql://localhost:5434/event_tickets` |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` |
| `JWT_SECRET` | dev-only value. **Set a random secret of 32+ characters in production** |
| `JWT_EXPIRATION` | `1h` |
| `PORT` | `8090` |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@tickets.local` / `Admin@12345` |
| `BOOKING_MAX_TICKETS_PER_CUSTOMER` | `8` (seats per customer per showtime) |
| `BOOKING_CANCEL_CUTOFF` | `2h` (customers can't cancel a paid booking after this point before the start) |
| `BOOKING_HOLD_DURATION` | `10m` (how long unpaid seats are held) |
| `BOOKING_PREVENT_SINGLE_SEAT_GAPS` | `true` (rule 13) |
| `BOOKING_EXPIRY_SWEEP_INTERVAL` | `PT1M` (how often expired holds are released, ISO-8601) |
| `CINEMA_TIME_ZONE` | `Asia/Phnom_Penh` (used for age checks) |
| `CINEMA_CLEANING_TIME` | `15m` (gap after each movie before the hall is free) |
| `CINEMA_CHECK_IN_OPENS_BEFORE` | `1h` |

With Docker Compose, `BOOKING_HOLD_DURATION` and `BOOKING_CANCEL_CUTOFF` are passed through to the API container, for example `BOOKING_HOLD_DURATION=1m docker compose up -d`.

## API

| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/register` | public (always creates a CUSTOMER; `dateOfBirth` optional) |
| POST | `/api/auth/login` | public → returns `accessToken` |
| GET | `/api/auth/me` | authenticated |
| GET | `/api/movies`, `/api/movies/{id}` | public |
| POST | `/api/movies` | ADMIN |
| PUT | `/api/movies/{id}` | ADMIN |
| GET | `/api/halls`, `/api/halls/{id}` | public |
| POST | `/api/halls` | ADMIN |
| GET | `/api/showtimes?movieId=` | public (upcoming = not ended yet; `movieId` optional) |
| GET | `/api/showtimes/{id}` | public |
| GET | `/api/showtimes/{id}/seats` | public (seat map with prices and availability) |
| POST | `/api/showtimes` | ADMIN |
| PUT | `/api/showtimes/{id}` | ADMIN |
| POST | `/api/showtimes/{id}/cancel` | ADMIN |
| POST | `/api/bookings` | authenticated `{ "showtimeId": 1, "seats": ["F1", "F2"] }`, optional `Idempotency-Key` header |
| GET | `/api/bookings` | own bookings (admin: all) |
| GET | `/api/bookings/{id}` | owner or admin |
| POST | `/api/bookings/{id}/pay` | owner or admin → returns `ticketCode` |
| POST | `/api/bookings/{id}/cancel` | owner or admin |
| POST | `/api/tickets/{code}/check-in` | ADMIN (staff at the door) |

Send the token as `Authorization: Bearer <accessToken>`. Ready-made requests are in [`api.http`](api.http).

## Tests

```powershell
./mvnw test      # 22 unit tests (BookingServiceTest, ShowtimeTest), no database needed
./mvnw verify    # + 14 integration tests (BookingApiIT): end-to-end and concurrency tests against event_tickets_test
```
