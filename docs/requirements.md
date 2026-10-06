# Requirements

This document specifies what the Cinema Tickets API must do. The starting point is the homework brief *"Homework · Event Tickets API: Business rules to implement"* ([`photo_2026-10-05_20-15-12.jpg`](photo_2026-10-05_20-15-12.jpg)). The brief's six rules were written for events with a seat count; the project has since grown into a cinema booking system with numbered seats, and the rules are re-applied to seats. Where the brief is silent, the decisions made are listed under [Assumptions](#5-assumptions-and-decisions).

## 1. Overview

The Cinema Tickets API is a backend REST service where:

- **admins** add movies, set up halls with numbered seats, schedule showtimes with a base ticket price, and check tickets in at the door,
- **customers** register, log in, pick seats on a seat map, hold them, pay (simulated) and receive a ticket code,
- the system guarantees that a seat is never sold twice, prices can't be tampered with, showtimes never overlap in a hall, and customers can only see their own bookings.

### 1.1 In scope

- User registration and login with JWT, with an optional date of birth for age-rated movies
- Movie management: create, update, list, view
- Hall management: create a hall with its seat layout (rows of STANDARD, VIP or COUPLE seats), list, view
- Showtime management: schedule, update, cancel, list upcoming, view, seat map
- Seat selection: book specific seats by label (for example `F7`)
- Booking flow: hold seats for 10 minutes, pay (simulated), list, view, cancel; unpaid holds expire
- Ticket check-in at the door
- The six business rules from the brief, four from the first review and seven cinema rules
- PostgreSQL persistence with versioned migrations
- Automated unit and integration tests

### 1.2 Out of scope

| Not included | Notes |
|---|---|
| Real payments and refunds | `POST /api/bookings/{id}/pay` only simulates payment: it confirms the booking and issues a ticket code. No money is collected or refunded |
| Editing or deleting halls | A hall's seat layout is fixed once created, so seat ids stay valid for bookings |
| Deleting movies or showtimes | Showtimes are cancelled, not deleted |
| Changing your profile (for example adding a date of birth later) | Date of birth can only be given at registration |
| Email, SMS, notifications | — |
| Password reset, email verification | — |
| Refresh tokens / logout / token revocation | Tokens expire after `JWT_EXPIRATION` (default 1h) |
| User management API for admins; a separate staff role | The only admin is created from configuration. Admins act as door staff for check-in |
| Pagination and search of lists | Lists return all items. Showtimes can be filtered by `movieId` |
| Frontend / UI | API only |

## 2. Actors and roles

| Actor | How they get access | Summary of permissions |
|---|---|---|
| **Visitor** (not logged in) | — | View movies, halls, showtimes and seat maps; register; log in |
| **Customer** | Self-registers through `POST /api/auth/register` | Everything a visitor can do, plus book seats, pay for, view and cancel **their own** bookings |
| **Admin** | Created on startup from `app.admin.*` configuration | Everything a customer can do, plus manage movies, halls and showtimes, view, pay for or cancel **any** booking (also inside the 2-hour deadline), and check tickets in |

## 3. Functional requirements

Each requirement has an ID so code reviews and tests can refer to it. **BR-xx** marks the business rules; their numbers match [business-rules.md](business-rules.md).

### 3.1 Authentication (AUTH)

| ID | Requirement | Error cases |
|---|---|---|
| AUTH-01 | A visitor can register with email, password (8–72 chars), full name and an optional date of birth (must be in the past). The new account always gets the **CUSTOMER** role. | 400 invalid input; 409 email already registered |
| AUTH-02 | Emails are unique and case-insensitive (`Alice@X.com` = `alice@x.com`). | 409 |
| AUTH-03 | A user can log in with email and password and receives a signed JWT access token with its expiry time. | 401 wrong email or password (same message for both) |
| AUTH-04 | Registration also returns a token, so the user is logged in immediately. | — |
| AUTH-05 | A logged-in user can view their own profile (`GET /api/auth/me`), including the date of birth. | 401 |
| AUTH-06 | Passwords are stored only as BCrypt hashes, never in plain text. | — |
| AUTH-07 | An admin account is created on startup from configuration if it doesn't exist yet. | — |

### 3.2 Movies (MOV)

| ID | Requirement | Error cases |
|---|---|---|
| MOV-01 | Anyone, including visitors, can list all movies (ordered by title) and view one. | 404 |
| MOV-02 | An admin can create a movie with title (≤ 200 chars), optional description, duration (1–600 minutes) and age rating `G`, `PG13` or `R18`. | 400; 401; 403 not an admin |
| MOV-03 | An admin can update a movie. Existing showtimes keep the end time they were scheduled with. | 400; 404; 403 |

### 3.3 Halls (HALL)

| ID | Requirement | Error cases |
|---|---|---|
| HALL-01 | Anyone can list halls (ordered by name) and view one with all its seats. | 404 |
| HALL-02 | An admin can create a hall with a name and 1–26 rows. Each row has a label of 1–3 capital letters, 1–50 seats numbered from 1, and a seat type `STANDARD`, `VIP` or `COUPLE`. Seat labels are row + number, for example `F7`. | 400 invalid input or a row defined twice; 403 |
| HALL-03 | Hall names are unique, ignoring case. | 409 |
| HALL-04 | A hall's seat layout can't be changed after it is created. | — |

### 3.4 Showtimes (SHOW)

| ID | Requirement | Error cases |
|---|---|---|
| SHOW-01 | Anyone can list showtimes that haven't ended yet, soonest first, optionally for one movie (`?movieId=`). Each shows movie, age rating, hall, start and end time, base price, status, total seats and available seats. | — |
| SHOW-02 | Anyone can view one showtime. | 404 |
| SHOW-03 | Anyone can view a showtime's seat map: every seat with its label, row, number, type, price and whether it can be booked right now. | 404 |
| SHOW-04 | An admin can schedule a showtime with movie, hall, start time (in the future) and base price (≥ 0). `endsAt` = start + movie duration + cleaning time. It starts as `SCHEDULED`. | 400 (BR-08); 404 movie or hall; 409 hall busy (BR-16); 403 |
| SHOW-05 | An admin can update a showtime. Once seats are held or sold, only the base price can change. | 400; 404; 409 (BR-16, BR-17) or cancelled |
| SHOW-06 | An admin can cancel a showtime, which sets its status to `CANCELLED` and cancels its bookings. | 404; 409 already cancelled |

### 3.5 Bookings (BKG)

| ID | Requirement | Error cases |
|---|---|---|
| BKG-01 | A logged-in user can book seats by sending `showtimeId` and a list of 1–4 seat labels. A new booking is `HELD` until `holdExpiresAt` (10 minutes) and has no ticket code yet. | 400; 401; 403 (BR-14); 404 showtime not found; 409 (BR-01, 05, 07, 08, 13) |
| BKG-02 | The booking request may carry an `Idempotency-Key` header (1–100 chars). Repeating it returns the first booking. | 400; 409 (BR-12) |
| BKG-03 | A user can pay for a held booking (`POST /api/bookings/{id}/pay`, simulated). It becomes `CONFIRMED` and gets a ticket code such as `K7QW-M2XP`. | 403 (BR-06); 404; 409 hold expired, already paid, showtime cancelled or started |
| BKG-04 | A customer can list their own bookings, newest first. An admin listing bookings sees everyone's. | 401 |
| BKG-05 | A user can view one booking: showtime, movie, hall, seats with type and price, total, status, hold expiry, ticket code and timestamps. An unpaid hold past its expiry shows as `EXPIRED`. | 403 (BR-06); 404 |
| BKG-06 | A user can cancel a booking. It becomes `CANCELLED`, the cancellation time is recorded, and its seats are freed. | 403 (BR-06); 404; 409 already cancelled or expired, checked in, or inside the deadline (BR-09) |
| BKG-07 | Each booking seat records the price at the time of booking. Later changes to the showtime price don't affect existing bookings. | — |
| BKG-08 | Unpaid holds expire: their seats are free again right away, and the booking becomes `EXPIRED`. | — |

### 3.6 Tickets (TKT)

| ID | Requirement | Error cases |
|---|---|---|
| TKT-01 | An admin can check a ticket in by its code (`POST /api/tickets/{code}/check-in`). The code is not case-sensitive. The response is the booking with `checkedInAt` set. | 403 not an admin; 404 unknown code; 409 (BR-15) |

### 3.7 Business rules (BR)

Rules 01–06 are the original homework brief, re-applied to seats. Rules 07–10 came from the first review. Rules 11–17 come with the cinema model.

| ID | Rule | Expected behaviour | Status |
|---|---|---|---|
| **BR-01** | **No overbooking** | A seat can be held or sold only once per showtime. This must also hold when many users book at the same time. | **409 Conflict** |
| **BR-02** | **Seats stay correct** | Holding takes the seats. Cancelling, hold expiry and showtime cancellation free them, exactly once. A second cancel is rejected. | — / **409** |
| **BR-03** | **1–4 seats per booking** | A booking lists 1 to 4 seats. Unknown or duplicate seat labels are rejected. | **400 Bad Request** |
| **BR-04** | **Price from the server** | Seat price = showtime base price × seat type multiplier (STANDARD 1.00, VIP 1.50, COUPLE 2.00), stored per booking seat. Any price sent by the client is ignored. | — |
| **BR-05** | **No booking a cancelled showtime** | Booking a showtime whose status is `CANCELLED` is rejected. | **409 Conflict** |
| **BR-06** | **Only your own bookings** | Customers can view, pay for or cancel only their own bookings. Admins can access all. | **403 Forbidden** |
| **BR-07** | **Max 8 seats per customer per showtime** | A customer's seats in `HELD` and `CONFIRMED` bookings for one showtime can't exceed 8 (`BOOKING_MAX_TICKETS_PER_CUSTOMER`). | **409 Conflict** |
| **BR-08** | **Sales close at the start** | Booking or paying once the showtime has started is rejected. Showtimes must be created with a future `startsAt`. | **409** / **400** |
| **BR-09** | **Cancel deadline** | Customers can't cancel a paid booking within 2 hours of the start (`BOOKING_CANCEL_CUTOFF`). Admins can. Held bookings can be released at any time. | **409 Conflict** |
| **BR-10** | **Showtime cancellation cascades** | Cancelling a showtime cancels all its held and confirmed bookings and frees every seat. | — |
| **BR-11** | **Hold, then pay** | A new booking is held for 10 minutes (`BOOKING_HOLD_DURATION`). Paying confirms it and issues a ticket code. Paying an expired hold is rejected. Expired holds count as free immediately and are cleaned up every minute. | **409 Conflict** |
| **BR-12** | **Idempotent booking** | Same user + same `Idempotency-Key` returns the first booking, no duplicate. The same key for a different showtime or different seats is rejected. Key must be 1–100 chars. | **409** / **400** |
| **BR-13** | **No single-seat gaps** | A choice can't leave exactly one empty seat between two taken seats in a row; row ends don't count (`BOOKING_PREVENT_SINGLE_SEAT_GAPS`). | **409 Conflict** |
| **BR-14** | **Age rating** | PG13 and R18 movies need a date of birth; the customer must be old enough on the showtime's date in the cinema's time zone (`CINEMA_TIME_ZONE`). | **403 Forbidden** |
| **BR-15** | **Ticket check-in** | Only `CONFIRMED` tickets, from 1 hour before the start (`CINEMA_CHECK_IN_OPENS_BEFORE`) until the showtime ends, once. Checked-in bookings can't be cancelled. | **409 Conflict** |
| **BR-16** | **No overlapping showtimes** | A hall is busy from `startsAt` to `startsAt + duration + 15 min cleaning` (`CINEMA_CLEANING_TIME`). Overlapping scheduled showtimes are rejected. | **409 Conflict** |
| **BR-17** | **Showtimes with bookings can't move** | Once seats are held or sold, movie, hall and start time are fixed. The price can still change. | **409 Conflict** |

Implementation details and test coverage for each rule: [business-rules.md](business-rules.md).

## 4. Non-functional requirements

| ID | Area | Requirement |
|---|---|---|
| NFR-01 | Security | All endpoints except register, login, reading movies/halls/showtimes (`GET`) and health need a valid JWT. A missing, invalid or expired token gets **401**. Writing movies, halls and showtimes and checking tickets in need the ADMIN role, otherwise **403**. |
| NFR-02 | Security | The caller's identity comes only from the verified token, never from the request body or URL. |
| NFR-03 | Security | Secrets (JWT key, admin password, DB password) can be set through environment variables. The JWT key must be ≥ 32 characters, or the app refuses to start. |
| NFR-04 | Concurrency | BR-01, BR-02, BR-07, BR-10 and BR-16 must hold under concurrent requests, without deadlocks. Every write locks the showtime row first, then booking rows. Verified by `BookingApiIT.concurrentBookingsSellEachSeatOnce` (20 customers race for the same 2 seats → exactly 1 succeeds) and `cancellingShowtimeWhileCustomersCancelNeverDeadlocks` (fails with `deadlock detected` if the lock order is reversed). |
| NFR-05 | Data integrity | The database enforces the core rules too: a seat is active once per showtime (`ux_booking_seats_active`), no overlapping scheduled showtimes per hall (`ex_showtimes_hall_overlap`), `1 ≤ quantity ≤ 4`, `ends_at > starts_at`, prices ≥ 0, unique email, hall name, seat position, ticket code and (user, idempotency key), valid enum values, foreign keys. |
| NFR-06 | Data integrity | Money uses exact decimals (`BigDecimal` / `NUMERIC`), never floating point. |
| NFR-07 | API consistency | Errors use RFC 9457 `application/problem+json`. Validation errors list the failing fields. |
| NFR-08 | API consistency | Creating a resource returns **201** with a `Location` header. |
| NFR-09 | Maintainability | Code is organized by feature package (`auth`, `movie`, `hall`, `showtime`, `booking`, `user`) with Controller → Service → Repository layers, DTOs for all request and response bodies, and constructor injection. |
| NFR-10 | Maintainability | Schema changes go through versioned Flyway migrations. Hibernate only validates the schema. Time-based rules read the time from an injectable `Clock`. |
| NFR-11 | Configurability | Booking and cinema policies (`app.booking.*`, `app.cinema.*`) can be changed through environment variables without code changes. |
| NFR-12 | Testability | Unit tests run without a database (`./mvnw test`). Integration tests run against real PostgreSQL with a controllable clock (`./mvnw verify`). |
| NFR-13 | Operability | A health endpoint (`/actuator/health`) is available for monitoring. Expired holds are swept by a scheduled job every minute. |
| NFR-14 | Portability | Runs anywhere with JDK 25 and PostgreSQL 15+ (with the `btree_gist` extension, which is trusted and ships with PostgreSQL). The Maven wrapper is included, and `docker-compose.yml` provides PostgreSQL. |

## 5. Assumptions and decisions

The brief doesn't cover these points. Here is what was decided:

| # | Question | Decision | Easy to change? |
|---|---|---|---|
| A-1 | Is the 4-seat limit per booking or per customer per showtime? | **Per booking**, as the brief says, **plus** at most 8 per customer per showtime across held and paid bookings (BR-07). | Yes: `BOOKING_MAX_TICKETS_PER_CUSTOMER` |
| A-2 | What happens to existing bookings when a showtime is cancelled? | **They're cancelled too**, held and paid alike (BR-10), and all seats are freed. | — |
| A-3 | Can admins see or cancel other people's bookings? | **Yes.** BR-06 limits customers only. | Yes: `BookingService.checkAccess` |
| A-4 | Booking that doesn't exist vs. belongs to someone else? | 404 if it doesn't exist; **403** if it belongs to someone else (as BR-06 states). | — |
| A-5 | What if the client sends a price? | **Ignored silently**, not rejected (BR-04 says "never trust", not "reject"). | Yes: enable strict JSON parsing |
| A-6 | Can someone register as an admin? | **No.** Registration always creates customers. The admin comes from configuration. | — |
| A-7 | Can showtimes in the past be booked? | **No.** Sales close when the showtime starts, and showtimes can't be scheduled in the past (BR-08). | — |
| A-8 | Second cancel of the same booking? | **409**, and seats are not freed twice (protects BR-02). | — |
| A-9 | Can a booking be cancelled right before the showtime? | **Paid, customer: no**, not within 2 hours of the start. **Admins: yes.** **Held (unpaid) bookings: yes**, at any time, since nothing was paid (BR-09). | Yes: `BOOKING_CANCEL_CUTOFF` |
| A-10 | How are seats identified? | By label, row + number (`F7`), unique within a hall. Labels are trimmed and upper-cased. | — |
| A-11 | How do seat types affect price? | Base price × multiplier: STANDARD 1.00, VIP 1.50, COUPLE 2.00 (a two-person sofa sold as one seat). | Yes: `SeatType` |
| A-12 | How long is a hold, and does an unpaid hold count against a customer? | **10 minutes**; held seats count towards BR-07 until they expire or are released. | Yes: `BOOKING_HOLD_DURATION` |
| A-13 | Whose clock decides age? | The customer's age on the **local date** of the showtime in `Asia/Phnom_Penh`. Without a date of birth, PG13/R18 can't be booked. | Yes: `CINEMA_TIME_ZONE` |
| A-14 | Who checks tickets in? | **Admins** act as door staff; there is no separate staff role. | — |
| A-15 | What happened to the old event data? | It isn't migrated: events had no hall or movie, so they can't become showtimes. The migrations were squashed into one `V1__init_schema.sql` with the cinema schema, so databases from the event model must be recreated (see [database.md](database.md#migrations)). | — |
| A-16 | How much time does a hall need between showtimes? | **15 minutes** of cleaning after each movie (BR-16). | Yes: `CINEMA_CLEANING_TIME` |

## 6. Acceptance criteria

The project is accepted when:

- [x] Every requirement in §3 is implemented.
- [x] Each BR-01…BR-17 returns the status code listed in §3.7.
- [x] `./mvnw test` passes: 22 unit tests (18 in `BookingServiceTest`, 4 in `ShowtimeTest`).
- [x] `./mvnw verify` passes against PostgreSQL: 14 integration tests in `BookingApiIT`, including the concurrent booking and deadlock tests.
- [x] The manual walkthrough in [testing-with-curl.md](testing-with-curl.md) gives the expected results.
- [x] The manual walkthrough in [testing-with-powershell.md](testing-with-powershell.md) gives the expected results.
- [x] The manual Postman test plan in [testing-with-postman.md](testing-with-postman.md) gives the expected results.
- [x] The documentation in `docs/` describes how to run, use and extend the system.

## 7. Traceability

| Requirement | Main code | Tests |
|---|---|---|
| AUTH-01…07 | `auth/AuthService`, `auth/dto/RegisterRequest`, `config/SecurityConfig`, `config/AdminAccountInitializer` | `BookingApiIT.securityRules`, `ageRatingNeedsOldEnoughCustomer` |
| MOV-01…03 | `movie/MovieService`, `movie/Movie`, `config/SecurityConfig` | `BookingApiIT.securityRules` |
| HALL-01…04 | `hall/HallService`, `hall/Hall`, `hall/Seat` | used by every `BookingApiIT` test |
| SHOW-01…06 | `showtime/ShowtimeService`, `showtime/Showtime`, `showtime/ShowtimeRepository` | `BookingApiIT.showtimesCantOverlapInAHallOrMoveAfterSales`, `cancellingAShowtimeCancelsItsBookings` |
| BKG-01…08 | `booking/BookingService`, `booking/Booking`, `booking/BookingController`, `booking/HoldExpiryJob` | `BookingApiIT.fullBookingFlowFollowsBusinessRules`, `unpaidHoldsExpireAndReleaseTheirSeats` |
| TKT-01 | `booking/TicketController`, `BookingService.checkIn`, `Booking.checkIn` | `BookingServiceTest.ticketChecksInOnceInsideTheWindow`, `BookingApiIT.salesCloseAtStartCancelDeadlineAndCheckIn` |
| BR-01 | `BookingService.checkSeatsFree`, `ShowtimeRepository.findByIdForUpdate`, index `ux_booking_seats_active` | `BookingServiceTest.createRejectsTakenSeats`, `BookingApiIT.concurrentBookingsSellEachSeatOnce`, `fullBookingFlowFollowsBusinessRules` |
| BR-02 | `Booking.cancel`, `BookingSeat.release`, `BookingSeatRepository.releaseExpiredHolds` / `releaseAllForShowtime` | `BookingServiceTest.cancelReleasesSeatsExactlyOnce`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` |
| BR-03 | `BookingRequest` (`@Size`), `BookingService.resolveSeats` | `BookingServiceTest.createRejectsUnknownAndDuplicateSeats`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` |
| BR-04 | `Booking` constructor, `Showtime.priceFor`, `SeatType` | `ShowtimeTest.seatPriceDependsOnSeatType`, `BookingServiceTest.createHoldsSeatsWithServerPrices`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` |
| BR-05 | `Showtime.checkOpenForSale` | `ShowtimeTest.cancelledShowtimeIsNotForSale`, `BookingServiceTest.createRejectsStartedOrCancelledShowtime`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` |
| BR-06 | `BookingService.checkAccess` | `BookingServiceTest.customerCannotPayViewOrCancelSomeoneElsesBooking`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` |
| BR-07 | `BookingService.checkCustomerLimit`, `BookingRepository.sumQuantity` | `BookingServiceTest.createRejectsCustomerOverTheLimit`, `BookingApiIT.customerCannotHoldMoreThanEightSeatsForOneShowtime` |
| BR-08 | `Showtime.checkOpenForSale`, `ShowtimeRequest` (`@Future`) | `ShowtimeTest.salesCloseWhenTheShowtimeStarts`, `BookingServiceTest.createRejectsStartedOrCancelledShowtime`, `BookingApiIT.salesCloseAtStartCancelDeadlineAndCheckIn`, `showtimesCantOverlapInAHallOrMoveAfterSales` |
| BR-09 | `BookingService.checkCancelDeadline` | `BookingServiceTest.customerCannotCancelPaidBookingWithinTwoHours`, `adminCanCancelPaidBookingWithinTwoHours`, `BookingApiIT.salesCloseAtStartCancelDeadlineAndCheckIn`, `adminCanCancelPaidBookingWithinDeadline` |
| BR-10 | `ShowtimeService.cancel`, `BookingService.cancelAllForShowtime` | `BookingApiIT.cancellingAShowtimeCancelsItsBookings`, `cancellingShowtimeWhileCustomersCancelNeverDeadlocks` |
| BR-11 | `BookingService.create` / `pay` / `expireHolds`, `Booking.pay`, `HoldExpiryJob`, `TicketCodeGenerator` | `BookingServiceTest.createExpiresOldHoldsFirst`, `payConfirmsAndIssuesTicketCode`, `payAfterHoldExpiredIsRejected`, `BookingApiIT.unpaidHoldsExpireAndReleaseTheirSeats`, `expiryJobReleasesExpiredHolds` |
| BR-12 | `BookingController.create`, `BookingService.replay`, constraint `ux_bookings_idempotency` | `BookingServiceTest.sameIdempotencyKeyReturnsTheFirstBooking`, `sameIdempotencyKeyWithDifferentSeatsIsRejected`, `BookingApiIT.repeatedRequestWithSameIdempotencyKeyReturnsTheSameBooking` |
| BR-13 | `BookingService.checkNoSingleSeatGaps` | `BookingServiceTest.createRejectsSingleSeatGap`, `singleSeatGapRuleCanBeTurnedOff`, `BookingApiIT.choiceThatLeavesASingleEmptySeatIsRejected` |
| BR-14 | `BookingService.checkAge`, `AgeRating` | `BookingServiceTest.restrictedMovieNeedsAgeOnShowDay`, `BookingApiIT.ageRatingNeedsOldEnoughCustomer` |
| BR-15 | `BookingService.checkIn`, `Booking.checkIn`, `Booking.cancel` | `BookingServiceTest.ticketChecksInOnceInsideTheWindow`, `BookingApiIT.salesCloseAtStartCancelDeadlineAndCheckIn` |
| BR-16 | `ShowtimeService.checkHallFree`, `ShowtimeRepository.existsOverlap`, constraint `ex_showtimes_hall_overlap` | `BookingApiIT.showtimesCantOverlapInAHallOrMoveAfterSales` |
| BR-17 | `Showtime.update`, `BookingService.hasActiveSeats` | `ShowtimeTest.startTimeCantMoveOnceSeatsAreSold`, `BookingApiIT.showtimesCantOverlapInAHallOrMoveAfterSales` |
