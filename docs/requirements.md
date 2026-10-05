# Requirements

This document specifies what the Event Tickets API must do. The source is the homework brief *"Homework · Event Tickets API: Business rules to implement"* ([`photo_2026-10-05_20-15-12.jpg`](photo_2026-10-05_20-15-12.jpg)). Where the brief is silent, the decisions made are listed under [Assumptions](#5-assumptions-and-decisions).

## 1. Overview

The Event Tickets API is a backend REST service where:

- **admins** publish events with a fixed number of seats and a ticket price,
- **customers** register, log in, and book tickets for those events,
- the system guarantees that seats are never oversold, prices can't be tampered with, and customers can only see their own bookings.

### 1.1 In scope

- User registration and login with JWT
- Event management: create, update, cancel, list, view
- Booking management: book, list, view, cancel
- The six business rules from the brief
- PostgreSQL persistence with versioned migrations
- Automated unit and integration tests

### 1.2 Out of scope

| Not included | Notes |
|---|---|
| Payments and refunds | `totalPrice` is recorded, but no money is collected |
| Seat selection / seat map | Seats are a count, not numbered places |
| Email, SMS, notifications | — |
| Password reset, email verification | — |
| Refresh tokens / logout / token revocation | Tokens expire after `JWT_EXPIRATION` (default 1h) |
| User management API for admins | The only admin is created from configuration |
| Pagination, search, filtering of lists | Lists return all items |
| Frontend / UI | API only |

## 2. Actors and roles

| Actor | How they get access | Summary of permissions |
|---|---|---|
| **Visitor** (not logged in) | — | View events; register; log in |
| **Customer** | Self-registers through `POST /api/auth/register` | Everything a visitor can do, plus book tickets and view or cancel **their own** bookings |
| **Admin** | Created on startup from `app.admin.*` configuration | Everything a customer can do, plus create, update and cancel events and view or cancel **any** booking |

## 3. Functional requirements

Each requirement has an ID so code reviews and tests can refer to it. **BR-xx** marks the six business rules from the brief.

### 3.1 Authentication (AUTH)

| ID | Requirement | Error cases |
|---|---|---|
| AUTH-01 | A visitor can register with email, password (8–72 chars) and full name. The new account always gets the **CUSTOMER** role. | 400 invalid input; 409 email already registered |
| AUTH-02 | Emails are unique and case-insensitive (`Alice@X.com` = `alice@x.com`). | 409 |
| AUTH-03 | A user can log in with email and password and receives a signed JWT access token with its expiry time. | 401 wrong email or password (same message for both) |
| AUTH-04 | Registration also returns a token, so the user is logged in immediately. | — |
| AUTH-05 | A logged-in user can view their own profile (`GET /api/auth/me`). | 401 |
| AUTH-06 | Passwords are stored only as BCrypt hashes, never in plain text. | — |
| AUTH-07 | An admin account is created on startup from configuration if it doesn't exist yet. | — |

### 3.2 Events (EVT)

| ID | Requirement | Error cases |
|---|---|---|
| EVT-01 | Anyone, including visitors, can list all events, ordered by start time. | — |
| EVT-02 | Anyone can view a single event, including its `availableSeats` and `status`. | 404 |
| EVT-03 | An admin can create an event with title, optional description, venue, start time, price (≥ 0) and total seats (≥ 1). It starts as `SCHEDULED`, with `availableSeats = totalSeats`. | 400; 401; 403 not an admin |
| EVT-04 | An admin can update an event. Seats already booked are kept: `availableSeats = newTotal − booked`. | 400; 404; 409 if the new total is below the seats already booked |
| EVT-05 | An admin can cancel an event, which sets its status to `CANCELLED`. | 404; 409 already cancelled |
| EVT-06 | Only admins can create, update or cancel events. | 403 |

### 3.3 Bookings (BKG)

| ID | Requirement | Error cases |
|---|---|---|
| BKG-01 | A logged-in user can book tickets by sending `eventId` and `quantity`. A successful booking is `CONFIRMED`. | 400; 401; 404 event not found; 409 (see BR-01, BR-05) |
| BKG-02 | A customer can list their own bookings, newest first. An admin listing bookings sees everyone's. | 401 |
| BKG-03 | A user can view one booking. | 403 (BR-06); 404 |
| BKG-04 | A user can cancel a booking. It becomes `CANCELLED`, and the cancellation time is recorded. | 403 (BR-06); 404; 409 already cancelled |
| BKG-05 | Each booking records the unit price at the time of booking. Later changes to the event price don't affect existing bookings. | — |

### 3.4 Business rules (BR): from the brief

| ID | Rule | Expected behaviour | Status |
|---|---|---|---|
| **BR-01** | **No overbooking** | Booking more tickets than seats left is rejected. This must also hold when many users book at the same time. | **409 Conflict** |
| **BR-02** | **Seats stay correct** | Booking reduces `availableSeats` by the quantity. Cancelling a booking gives the seats back, exactly once. | — |
| **BR-03** | **Max 4 tickets per booking** | `quantity` must be between 1 and 4. | **400 Bad Request** |
| **BR-04** | **Price from the server** | `totalPrice = event price × quantity`. Any price sent by the client is ignored. | — |
| **BR-05** | **No booking a cancelled event** | Booking an event whose status is `CANCELLED` is rejected. | **409 Conflict** |
| **BR-06** | **Only your own bookings** | Customers can view or cancel only their own bookings. | **403 Forbidden** |

Implementation details and test coverage for each rule: [business-rules.md](business-rules.md).

## 4. Non-functional requirements

| ID | Area | Requirement |
|---|---|---|
| NFR-01 | Security | All endpoints except register, login, viewing events and health need a valid JWT. A missing, invalid or expired token gets **401**. |
| NFR-02 | Security | The caller's identity comes only from the verified token, never from the request body or URL. |
| NFR-03 | Security | Secrets (JWT key, admin password, DB password) can be set through environment variables. The JWT key must be ≥ 32 characters, or the app refuses to start. |
| NFR-04 | Concurrency | BR-01 and BR-02 must hold under concurrent requests. Verified: 20 simultaneous bookings for 10 seats leave exactly 10 confirmed and 0 seats. |
| NFR-05 | Data integrity | The database enforces the core rules too: `0 ≤ available_seats ≤ total_seats`, `1 ≤ quantity ≤ 4`, unique email, valid enum values, foreign keys. |
| NFR-06 | Data integrity | Money uses exact decimals (`BigDecimal` / `NUMERIC`), never floating point. |
| NFR-07 | API consistency | Errors use RFC 9457 `application/problem+json`. Validation errors list the failing fields. |
| NFR-08 | API consistency | Creating a resource returns **201** with a `Location` header. |
| NFR-09 | Maintainability | Code is organized by feature package with Controller → Service → Repository layers, DTOs for all request and response bodies, and constructor injection. |
| NFR-10 | Maintainability | Schema changes go through versioned Flyway migrations. Hibernate only validates the schema. |
| NFR-11 | Testability | Unit tests run without a database (`./mvnw test`). Integration tests run against real PostgreSQL (`./mvnw verify`). |
| NFR-12 | Operability | A health endpoint (`/actuator/health`) is available for monitoring. |
| NFR-13 | Portability | Runs anywhere with JDK 25 and PostgreSQL 15+. The Maven wrapper is included, and `docker-compose.yml` provides PostgreSQL. |

## 5. Assumptions and decisions

The brief doesn't cover these points. Here is what was decided:

| # | Question | Decision | Easy to change? |
|---|---|---|---|
| A-1 | Is the 4-ticket limit per booking or per customer per event? | **Per booking**, as the brief says. A customer can make several bookings. | Yes: add a check in `BookingService.create` |
| A-2 | What happens to existing bookings when an event is cancelled? | **Nothing automatic.** They stay `CONFIRMED`, and customers can still cancel them to get their seats back. | Yes: cancel them in `EventService.cancel` |
| A-3 | Can admins see or cancel other people's bookings? | **Yes.** BR-06 limits customers only. | Yes: `BookingService.checkAccess` |
| A-4 | Booking that doesn't exist vs. belongs to someone else? | 404 if it doesn't exist; **403** if it belongs to someone else (as BR-06 states). | — |
| A-5 | What if the client sends a price? | **Ignored silently**, not rejected (BR-04 says "never trust", not "reject"). | Yes: enable strict JSON parsing |
| A-6 | Can someone register as an admin? | **No.** Registration always creates customers. The admin comes from configuration. | — |
| A-7 | Can events in the past be booked? | **Yes.** Not restricted by the brief. | Yes: add a check in `Event.reserveSeats` |
| A-8 | Second cancel of the same booking? | **409**, and seats are not returned twice (protects BR-02). | — |

## 6. Acceptance criteria

The project is accepted when:

- [x] Every requirement in §3 is implemented.
- [x] Each BR-01…BR-06 returns the status code required by the brief.
- [x] `./mvnw test` passes: 8 unit tests.
- [x] `./mvnw verify` passes against PostgreSQL: 3 integration tests, including the concurrency test.
- [x] The manual walkthrough in [testing-with-curl.md](testing-with-curl.md) gives the expected results.
- [x] The documentation in `docs/` describes how to run, use and extend the system.

## 7. Traceability

| Requirement | Main code | Tests |
|---|---|---|
| AUTH-01…07 | `auth/AuthService`, `config/SecurityConfig`, `config/AdminAccountInitializer` | `BookingApiIT.securityRules` |
| EVT-01…06 | `event/EventService`, `event/Event`, `config/SecurityConfig` | `BookingApiIT.securityRules`, `fullBookingFlowFollowsBusinessRules` |
| BR-01 | `Event.reserveSeats`, `EventRepository.findByIdForUpdate` | `BookingServiceTest.createRejectsOverbookingWithConflict`, `BookingApiIT.concurrentBookingsNeverOversell` |
| BR-02 | `BookingService.create` / `cancel`, `Event.releaseSeats`, `Booking.cancel` | `BookingServiceTest.cancelGivesSeatsBack`, `cancelTwiceIsConflictAndDoesNotReleaseSeatsAgain` |
| BR-03 | `BookingRequest` (`@Min`/`@Max`) | `BookingApiIT.fullBookingFlowFollowsBusinessRules` |
| BR-04 | `Booking` constructor, `Event.totalPriceFor` | `BookingServiceTest.createReducesSeatsAndComputesPriceOnServer`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` |
| BR-05 | `Event.reserveSeats` | `BookingServiceTest.createRejectsCancelledEventWithConflict`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` |
| BR-06 | `BookingService.checkAccess` | `BookingServiceTest.customerCannotViewOrCancelSomeoneElsesBooking`, `adminCanViewAnyBooking` |
