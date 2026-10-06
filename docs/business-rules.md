# Business rules

The six rules from the homework specification (01–06), four rules added after the first review (07–10), seven rules that come with the cinema model (11–17), and exactly where each one is enforced.

Rules 01–06 were written for events with a seat count. In the cinema model they apply to **numbered seats in a hall, per showtime**.

| # | Rule | Response |
|---|---|---|
| 01 | No overbooking: a seat is held or sold once per showtime | 409 Conflict |
| 02 | Seats stay correct | n/a (2nd cancel → 409) |
| 03 | 1–4 seats per booking; seat labels must exist and not repeat | 400 Bad Request |
| 04 | Price from the server | n/a |
| 05 | No booking a cancelled showtime | 409 Conflict |
| 06 | Only your own bookings | 403 Forbidden |
| 07 | Max 8 seats per customer per showtime | 409 Conflict |
| 08 | Sales close when the showtime starts; showtimes must start in the future | 409 / 400 |
| 09 | Customers can't cancel a paid booking within 2 hours of the start | 409 Conflict |
| 10 | Cancelling a showtime cancels its bookings | n/a |
| 11 | Hold, then pay within 10 minutes | 409 Conflict (expired hold) |
| 12 | Idempotent booking requests | 409 / 400 |
| 13 | No single empty seat between two taken seats | 409 Conflict |
| 14 | Age rating | 403 Forbidden |
| 15 | Ticket check-in | 409 Conflict |
| 16 | No overlapping showtimes in a hall | 409 Conflict |
| 17 | A showtime with bookings can't move | 409 Conflict |

---

## 01: No overbooking

> A seat can be held or sold **once per showtime**. Choosing a seat that is already taken returns **409 Conflict**.

**Enforced in:** `BookingService.checkSeatsFree`, called from `BookingService.create`.

```java
List<String> unavailable = chosen.stream()
    .filter((seat) -> taken.contains(seat.getId()))
    .map(Seat::getLabel)
    .toList();
if (!unavailable.isEmpty()) {
    throw new ConflictException("Seat(s) already taken: " + String.join(", ", unavailable));
}
```

A seat is **taken** while it has an active `booking_seats` row whose booking is `CONFIRMED`, or `HELD` with a hold that hasn't expired yet (`BookingSeatRepository.findTakenSeatIds`). Expired holds count as free straight away (rule 11).

**Concurrency safety.** Reading the taken seats and then inserting new ones is a race condition if two requests run at the same time. To prevent it, `BookingService.create` first loads the showtime with `ShowtimeRepository.findByIdForUpdate`, which issues `SELECT ... FOR UPDATE`. A second booking for the same showtime waits until the first transaction commits, then sees its seats as taken.

**Last line of defence.** The partial unique index

```sql
CREATE UNIQUE INDEX ux_booking_seats_active ON booking_seats (showtime_id, seat_id) WHERE active;
```

makes double-selling impossible even if application code is wrong. If it fires, `create` turns the `DataIntegrityViolationException` into a 409.

**Tested by:** `BookingServiceTest.createRejectsTakenSeats`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` and `BookingApiIT.concurrentBookingsSellEachSeatOnce`. The concurrency test has 20 customers book the same two seats (`A1`, `A2`) at the same moment. Exactly 1 succeeds and exactly 2 active booking seats exist afterwards.

## 02: Seats stay correct

> Holding a booking takes its seats. Cancelling it, letting the hold expire, or cancelling the showtime gives them back, **exactly once**.

There is no seat counter to keep in step. A seat is taken while its `booking_seats` row is `active`, so freeing seats means setting `active = false`:

- `BookingService.create`: `new Booking(...)` creates one active `BookingSeat` per chosen seat.
- `BookingService.cancel`: `Booking.cancel(now)` sets the status to `CANCELLED` and calls `BookingSeat.release()` on each seat.
- Hold expiry: `BookingSeatRepository.releaseExpiredHolds` then `BookingRepository.expireHolds` (rule 11).
- Showtime cancellation: `BookingSeatRepository.releaseAllForShowtime` then `BookingRepository.cancelAllForShowtime` (rule 10).

The available seat count shown on a showtime is computed on every read: seats in the hall minus taken seats (`0` for a cancelled showtime).

**Edge cases handled:**
- **Double cancel.** `Booking.cancel()` throws `ConflictException` (409) if the booking is already `CANCELLED` or `EXPIRED`, so seats are never released twice. The booking row is locked (`BookingRepository.findByIdForUpdate`), so two concurrent cancel requests cannot both pass the check.
- **Release runs once.** The bulk updates only touch rows that are still `active = true`.
- **Hall layout is fixed.** Halls can't be edited after they are created, so seat ids stay valid for existing bookings.

**Tested by:** `BookingServiceTest.cancelReleasesSeatsExactlyOnce`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` (held seats lower the available count from 80 to 77; cancelling brings it back to 80; a second cancel is 409), `unpaidHoldsExpireAndReleaseTheirSeats`, `expiryJobReleasesExpiredHolds`, `cancellingAShowtimeCancelsItsBookings`.

## 03: 1–4 seats per booking

> A booking lists 1 to 4 seats, otherwise **400 Bad Request**. Unknown or repeated seat labels are also **400**.

**Enforced in:** `BookingRequest`:

```java
@NotNull Long showtimeId,
@NotNull @Size(min = BookingRequest.MIN_SEATS, max = BookingRequest.MAX_SEATS,
        message = "must be between 1 and 4 seats") List<@NotBlank String> seats
```

Validation runs before the controller method (`@Valid`). The 400 response lists the failing field:

```json
{ "status": 400, "detail": "Validation failed", "errors": { "seats": "must be between 1 and 4 seats" } }
```

A database constraint `CHECK (quantity BETWEEN 1 AND 4)` on `bookings` backs this up.

**Seat labels.** `BookingService.resolveSeats` trims and upper-cases each label (`" a1 "` becomes `A1`), then:
- the same seat listed twice (`["A1", "a1"]`) → **400** "Seat A1 is listed twice"
- a label that isn't a seat in this showtime's hall (`Z9`) → **400** "Unknown seat(s) in this hall: Z9"

> **Note:** This limit applies **per booking**. Rule 07 adds a second limit across all of a customer's bookings for the same showtime.

**Tested by:** `BookingServiceTest.createRejectsUnknownAndDuplicateSeats`, `BookingApiIT.fullBookingFlowFollowsBusinessRules`.

## 04: Price from the server

> Seat price = showtime base price × seat type multiplier. Never trust a price sent by the client.

| Seat type | Multiplier | Example with base price 5.00 |
|---|---|---|
| `STANDARD` | × 1.00 | 5.00 |
| `VIP` | × 1.50 | 7.50 |
| `COUPLE` (a two-person sofa sold as one seat) | × 2.00 | 10.00 |

**Enforced in:**
- `BookingRequest` has **no price fields**. Unknown JSON properties such as `"totalPrice": 0.01` are silently ignored (Spring Boot's default Jackson setting).
- `SeatType.priceFor(basePrice)` multiplies and rounds to 2 decimals (`HALF_UP`).
- The `Booking` constructor prices every seat from the showtime:

```java
for (Seat seat : seats) {
    BigDecimal price = showtime.priceFor(seat.getSeatType());
    this.seats.add(new BookingSeat(this, showtime, seat, price));
    this.totalPrice = this.totalPrice.add(price);
}
```

The price of each seat is **stored on its booking seat** (`booking_seats.price`) at the moment of booking. If an admin later changes the showtime's base price (allowed, see rule 17), existing bookings keep the price the customer was quoted.

Money uses `BigDecimal` in Java and `NUMERIC(10,2)` / `NUMERIC(12,2)` in PostgreSQL, never `double`.

**Tested by:** `ShowtimeTest.seatPriceDependsOnSeatType`, `BookingServiceTest.createHoldsSeatsWithServerPrices`, `BookingApiIT.fullBookingFlowFollowsBusinessRules`. The integration test sends `totalPrice: 0.01` for `A1`, `A2` (STANDARD) and `B1` (VIP) at base price 5.00 and asserts the total is `17.50` and `B1` costs `7.50`.

## 05: No booking a cancelled showtime

> If the showtime status is `CANCELLED`, return **409 Conflict**.

**Enforced in:** `Showtime.checkOpenForSale(now)`, called from `BookingService.create` (and from `pay` for a held booking). It checks the status first:

```java
if (isCancelled()) {
    throw new ConflictException("Showtime %d is cancelled".formatted(id));
}
```

Showtimes are cancelled by an admin through `POST /api/showtimes/{id}/cancel`. Cancelling an already cancelled showtime returns 409. A cancelled showtime can't be updated either (409).

Existing bookings are cancelled along with the showtime: see rule 10.

**Tested by:** `ShowtimeTest.cancelledShowtimeIsNotForSale`, `BookingServiceTest.createRejectsStartedOrCancelledShowtime`, `BookingApiIT.fullBookingFlowFollowsBusinessRules`.

## 06: Only your own bookings

> Customers can view, pay for or cancel only their own bookings, otherwise **403 Forbidden**.

**Enforced in:** `BookingService.checkAccess`, called by `findById`, `pay` and `cancel`:

```java
if (!currentUser.isAdmin() && !booking.isOwnedBy(currentUser.id())) {
    throw new ForbiddenException("You can only access your own bookings");
}
```

- `GET /api/bookings` (list) is filtered by user ID in the query, so customers only ever see their own bookings.
- **Admins** can view, pay for and cancel any booking, and their list shows everyone's bookings.
- The caller's identity comes from the verified JWT `sub` claim, **never** from the request body or URL.
- A booking that doesn't exist returns 404. A booking that belongs to someone else returns 403, as the spec requires.

**Tested by:** `BookingServiceTest.customerCannotPayViewOrCancelSomeoneElsesBooking` (also checks that an admin can view it), `BookingApiIT.fullBookingFlowFollowsBusinessRules`.

---

## Additional rules

Rules 07–10 aren't in the brief. The first review found these gaps in rules 01–06: one customer could buy every seat through many small bookings, past showings could be sold, a booking could be cancelled right before the show, and cancelling a showing left its bookings active. The limits in 07 and 09 are settings in `application.yml` (`app.booking.*`).

## 07: Max 8 seats per customer per showtime

> A customer's seats for one showtime, across all their `HELD` and `CONFIRMED` bookings, can't exceed 8. Otherwise **409 Conflict**.

**Enforced in:** `BookingService.checkCustomerLimit`, which sums the customer's `HELD` + `CONFIRMED` quantity (`BookingRepository.sumQuantity`) **after** the showtime row is locked. Two concurrent bookings by the same customer for the same showtime therefore run one after another, so they can't both slip under the limit. Cancelled and expired bookings don't count, so cancelling (or letting a hold expire) frees up allowance again. Holds count, so a customer can't hold a whole row without paying.

**Setting:** `app.booking.max-tickets-per-customer` (env `BOOKING_MAX_TICKETS_PER_CUSTOMER`, default `8`).

**Tested by:** `BookingServiceTest.createRejectsCustomerOverTheLimit`, `BookingApiIT.customerCannotHoldMoreThanEightSeatsForOneShowtime`.

## 08: Sales close when the showtime starts; showtimes start in the future

> Booking (or paying for a held booking) at or after `startsAt` → **409 Conflict**. Creating or updating a showtime with `startsAt` in the past → **400 Bad Request**.

**Enforced in:** `Showtime.checkOpenForSale(now)`, which checks `hasStartedAt(now)` after the cancelled check. `@Future` on `ShowtimeRequest.startsAt`. The seat map shows every seat as unavailable once sales are closed.

The current time comes from a `Clock` bean (`ClockConfig`), so unit tests use a fixed clock and integration tests use a `MutableClock` they can move forward.

**Tested by:** `ShowtimeTest.salesCloseWhenTheShowtimeStarts`, `BookingServiceTest.createRejectsStartedOrCancelledShowtime`, `BookingApiIT.salesCloseAtStartCancelDeadlineAndCheckIn`, `BookingApiIT.showtimesCantOverlapInAHallOrMoveAfterSales` (past `startsAt` → 400).

## 09: Customers can't cancel a paid booking within 2 hours of the start

> A customer cancelling a `CONFIRMED` booking later than `startsAt − 2h` → **409 Conflict**. Admins can always cancel (for example, to handle a complaint at the cinema). A `HELD` booking can be released at any time.

**Enforced in:** `BookingService.checkCancelDeadline`, called by `cancel` only for customers and only when the booking is `CONFIRMED`. Cancelling exactly at the deadline is still allowed.

```java
if (!currentUser.isAdmin() && booking.getStatus() == BookingStatus.CONFIRMED) {
    checkCancelDeadline(booking.getShowtime(), now);
}
```

A booking that has been checked in can't be cancelled by anyone (rule 15).

**Setting:** `app.booking.cancel-cutoff` (env `BOOKING_CANCEL_CUTOFF`, default `2h`).

**Tested by:** `BookingServiceTest.customerCannotCancelPaidBookingWithinTwoHours` (also cancels exactly at the deadline), `adminCanCancelPaidBookingWithinTwoHours`, `BookingApiIT.salesCloseAtStartCancelDeadlineAndCheckIn`, `adminCanCancelPaidBookingWithinDeadline`.

## 10: Cancelling a showtime cancels its bookings

> `POST /api/showtimes/{id}/cancel` marks every `HELD` and `CONFIRMED` booking for the showtime `CANCELLED`, sets its `cancelledAt`, and frees every seat.

**Enforced in:** `ShowtimeService.cancel` locks the showtime, calls `Showtime.cancel()`, then `BookingService.cancelAllForShowtime`, which runs two bulk `UPDATE`s: `BookingSeatRepository.releaseAllForShowtime` (all seats `active = false`) and `BookingRepository.cancelAllForShowtime`. A customer cancelling at the same moment either finishes first or then gets 409 "already cancelled". The seats are never released twice.

**Tested by:** `BookingApiIT.cancellingAShowtimeCancelsItsBookings` (one paid and one held booking, both end `CANCELLED`, 0 active seats), `cancellingShowtimeWhileCustomersCancelNeverDeadlocks`, `fullBookingFlowFollowsBusinessRules`.

---

## Cinema rules

Rules 11–17 come with the move from events with a seat count to movies, halls with numbered seats, and showtimes. Their settings are in `application.yml` under `app.booking.*` and `app.cinema.*`.

## 11: Hold, then pay

> A new booking is `HELD` for 10 minutes. `POST /api/bookings/{id}/pay` (simulated payment) makes it `CONFIRMED` and issues a ticket code. Paying an expired hold → **409 Conflict**.

Booking statuses: `HELD → CONFIRMED → CANCELLED`, or `HELD → EXPIRED` (not paid in time) / `CANCELLED` (released by the customer or the showtime was cancelled).

**Enforced in:**
- `BookingService.create` sets `holdExpiresAt = now + holdDuration`; the booking starts `HELD` with no ticket code.
- `BookingService.pay` locks the booking, checks access (rule 06), checks the showtime is still open for sale if the booking is `HELD` (rules 05, 08), then calls `Booking.pay(now, ticketCode)`. That throws 409 if the hold has expired ("hold expired") or the booking isn't `HELD` (so paying twice is 409).
- `TicketCodeGenerator` makes random codes like `K7QW-M2XP` (no `0/O` or `1/I`). The unique constraint `ux_bookings_ticket_code` catches the unlikely collision.

**Expired holds** are handled in three places:
1. **Immediately, on read.** `findTakenSeatIds` treats a `HELD` booking with `holdExpiresAt <= now` as free, and `Booking.statusAt(now)` reports it as `EXPIRED` in responses before the row is updated.
2. **When someone books that showtime.** `BookingService.create` calls `expireHolds(showtimeId, now)` right after locking the showtime, so expired rows become `EXPIRED` and their seats `active = false` before the new seats are inserted (otherwise the unique index of rule 01 would block them).
3. **Every minute.** `HoldExpiryJob` finds showtimes with expired holds and expires them, locking each showtime first. This only keeps the rows tidy.

There is no real payment provider. The pay endpoint is the place where one would plug in.

**Settings:** `app.booking.hold-duration` (env `BOOKING_HOLD_DURATION`, default `10m`), `app.booking.expiry-sweep-interval` (env `BOOKING_EXPIRY_SWEEP_INTERVAL`, default `PT1M`).

**Tested by:** `BookingServiceTest.createHoldsSeatsWithServerPrices`, `createExpiresOldHoldsFirst`, `payConfirmsAndIssuesTicketCode`, `payAfterHoldExpiredIsRejected`, `BookingApiIT.unpaidHoldsExpireAndReleaseTheirSeats`, `expiryJobReleasesExpiredHolds`, `fullBookingFlowFollowsBusinessRules`.

## 12: Idempotent booking requests

> A client can send an `Idempotency-Key` header with `POST /api/bookings`. Repeating the request with the same key returns the **first booking** instead of creating a second one.

**Enforced in:**
- `BookingController.create`: a key that is blank or longer than 100 characters → **400**. The header is optional.
- `BookingService.create`: after locking the showtime, looks up `findByUserIdAndIdempotencyKey`. If found and it is for the same showtime and the same seats, the existing booking is returned (status 201, same `id`, current status). If it was for a **different showtime** → **409** "Idempotency key was already used for a different booking request".
- Keys are scoped per user: the constraint `ux_bookings_idempotency UNIQUE (user_id, idempotency_key)` backs this up. Two different customers can use the same key.

**Tested by:** `BookingServiceTest.sameIdempotencyKeyReturnsTheFirstBooking`, `sameIdempotencyKeyWithDifferentSeatsIsRejected`, `BookingApiIT.repeatedRequestWithSameIdempotencyKeyReturnsTheSameBooking` (one row in `bookings` after two requests; a new key creates a new booking).

## 13: No single empty seat between two taken seats

> A choice that leaves exactly **one** empty seat between two taken seats in the same row → **409 Conflict**. Row ends don't count as taken.

Nobody books a lone seat squeezed between strangers, so such gaps are lost sales. For example, with `A3` taken, booking `A1` alone (leaving `A2`) or `A5` alone (leaving `A4`) is rejected; `A1`+`A2` or `A6` are fine.

**Enforced in:** `BookingService.checkNoSingleSeatGaps`. For each chosen seat it looks one and two seats to the left and right: if the neighbour is free and the seat beyond it is taken (counting the seats being chosen), the neighbour would become a single gap. The error names the seats: "This choice would leave a single empty seat at A2. Choose seats next to each other, or leave at least two seats free".

**Setting:** `app.booking.prevent-single-seat-gaps` (env `BOOKING_PREVENT_SINGLE_SEAT_GAPS`, default `true`).

**Tested by:** `BookingServiceTest.createRejectsSingleSeatGap`, `singleSeatGapRuleCanBeTurnedOff`, `BookingApiIT.choiceThatLeavesASingleEmptySeatIsRejected`.

## 14: Age rating

> Movies are rated `G` (anyone), `PG13` (13+) or `R18` (18+). Booking a `PG13` or `R18` showtime without a date of birth, or while too young, → **403 Forbidden**.

**Enforced in:** `BookingService.checkAge`:
- `G` → no check.
- No `dateOfBirth` on the user → 403 "'…' is rated R18: add your date of birth to book it". Date of birth is optional at registration (`RegisterRequest.dateOfBirth`, must be in the past).
- Age is computed **on the showtime's date in the cinema's time zone**: `startsAt` is converted to a local date in `Asia/Phnom_Penh`, then `Period.between(dateOfBirth, showDate).getYears()`. Someone who turns 18 on the day of the showing may book it.

**Setting:** `app.cinema.time-zone` (env `CINEMA_TIME_ZONE`, default `Asia/Phnom_Penh`).

**Tested by:** `BookingServiceTest.restrictedMovieNeedsAgeOnShowDay`, `BookingApiIT.ageRatingNeedsOldEnoughCustomer`.

## 15: Ticket check-in

> Staff check a ticket in at the door with `POST /api/tickets/{code}/check-in` (ADMIN only). It works only for a `CONFIRMED` booking, from 1 hour before the start until the showtime ends, and only once. Otherwise **409 Conflict**.

**Enforced in:** `BookingService.checkIn` and `Booking.checkIn`:
- The code is trimmed and upper-cased, so `abcd-efgh` matches `ABCD-EFGH`. Unknown code → **404**.
- Before `startsAt − 1h` → 409 "Check-in for ticket … opens at …".
- At or after `endsAt` → 409 "Showtime … has ended".
- Booking not `CONFIRMED` (held, cancelled, expired) → 409.
- Already checked in → 409 "Ticket … was already checked in at …".
- On success `checkedInAt` is set. A checked-in booking **can't be cancelled** by anyone, admins included (409 "Booking … is already checked in").

**Setting:** `app.cinema.check-in-opens-before` (env `CINEMA_CHECK_IN_OPENS_BEFORE`, default `1h`).

**Tested by:** `BookingServiceTest.ticketChecksInOnceInsideTheWindow`, `BookingApiIT.salesCloseAtStartCancelDeadlineAndCheckIn`, `BookingApiIT.securityRules` (a customer gets 403).

## 16: No overlapping showtimes in a hall

> Two scheduled showtimes can't use the same hall at the same time. A showtime occupies its hall from `startsAt` until `endsAt = startsAt + movie duration + 15 min cleaning`. Overlap → **409 Conflict**.

**Enforced in:**
- `ShowtimeService.create` / `update` compute `endsAt` and call `ShowtimeRepository.existsOverlap` (same hall, `SCHEDULED`, `startsAt < other.endsAt and endsAt > other.startsAt`, excluding the showtime being updated).
- **Last line of defence:** the PostgreSQL exclusion constraint (needs the `btree_gist` extension)

```sql
CONSTRAINT ex_showtimes_hall_overlap EXCLUDE USING gist (hall_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
    WHERE (status = 'SCHEDULED')
```

catches two admins scheduling the same hall at the same moment; `ShowtimeService.saveAndFlush` turns it into a 409. Cancelled showtimes don't block the hall. A showtime may start exactly when the previous one's `endsAt` is reached.

If a movie's duration is edited later, existing showtimes keep the `endsAt` they were scheduled with.

**Setting:** `app.cinema.cleaning-time` (env `CINEMA_CLEANING_TIME`, default `15m`).

**Tested by:** `BookingApiIT.showtimesCantOverlapInAHallOrMoveAfterSales` (a 120-minute movie ends at start + 135 min; a second showtime at +134 min → 409, at +135 min → 201).

## 17: A showtime with bookings can't move

> Once any seat of a showtime is held or sold, its movie, hall and start time can't change → **409 Conflict**. The base price can still change; existing bookings keep their price (rule 04).

Customers chose and paid for that movie, in that hall, at that time. To move it, cancel the showtime (rule 10) and create a new one.

**Enforced in:** `Showtime.update(..., hasSales)`, where `hasSales` comes from `BookingService.hasActiveSeats` (any taken seat, expired holds not counted), read while the showtime row is locked by `ShowtimeService.update`.

**Tested by:** `ShowtimeTest.startTimeCantMoveOnceSeatsAreSold`, `BookingApiIT.showtimesCantOverlapInAHallOrMoveAfterSales`.

---

## Lock order

Every write that touches a showtime's seats or bookings locks the **showtime row first** (`ShowtimeRepository.findByIdForUpdate`), then booking rows:

| Operation | Locks |
|---|---|
| Create booking | showtime |
| Pay / cancel booking | reads the booking's showtime id without a lock (`findShowtimeIdById`), locks the showtime, then the booking (`findByIdForUpdate`) |
| Check in ticket | reads the showtime id by ticket code, locks the showtime, then the booking (`findByTicketCodeForUpdate`) |
| Update / cancel showtime | showtime, then bulk updates of its bookings and booking seats |
| Hold expiry job | showtime, then bulk updates of its expired holds |

One order everywhere means no deadlock. `BookingApiIT.cancellingShowtimeWhileCustomersCancelNeverDeadlocks` proves it: an admin cancels a showtime while 10 customers cancel their paid bookings at the same moment, 5 rounds. With the order reversed (booking first, then showtime) the test fails with `deadlock detected`.

---

## Order of checks when creating a booking

1. JWT valid? No → **401**
2. Body valid, `showtimeId` present and 1–4 non-blank seat labels? No → **400** *(rule 03)*
3. `Idempotency-Key` header present but blank or longer than 100 characters? Yes → **400** *(rule 12)*
4. Showtime exists? No → **404** *(row is now locked)*
5. Same user already used this `Idempotency-Key`? Same showtime and seats → return that booking, **201**; different showtime or seats → **409** *(rule 12)*
6. Expire this showtime's old holds in the database *(rule 11)*
7. Showtime cancelled? Yes → **409** *(rule 05)*
8. Showtime already started? Yes → **409** *(rule 08)*
9. Movie rated PG13/R18 and no date of birth, or too young on the show date? Yes → **403** *(rule 14)*
10. Seat listed twice, or not a seat in this hall? Yes → **400** *(rule 03)*
11. Customer would have more than 8 seats for this showtime? Yes → **409** *(rule 07)*
12. Any chosen seat already taken? Yes → **409** *(rule 01)*
13. Would leave a single empty seat between taken seats? Yes → **409** *(rule 13)*
14. Save the booking as `HELD` with server-computed seat prices; the unique index still turns a double-sold seat into **409** → **201** *(rules 01, 02, 04, 11)*

## Order of checks when paying for a booking

1. JWT valid? No → **401**
2. Booking exists? No → **404** *(then the showtime row, then the booking row are locked)*
3. Owner or admin? No → **403** *(rule 06)*
4. Booking `HELD` and showtime cancelled or started? Yes → **409** *(rules 05, 08)*
5. Hold expired? Yes → **409** *(rule 11)*
6. Not `HELD` (already paid, cancelled, expired)? Yes → **409** *(rule 11)*
7. Mark `CONFIRMED`, issue the ticket code → **200** *(rule 11)*

## Order of checks when cancelling a booking

1. JWT valid? No → **401**
2. Booking exists? No → **404** *(then the showtime row, then the booking row are locked)*
3. Owner or admin? No → **403** *(rule 06)*
4. Customer, booking `CONFIRMED`, and less than 2 hours before the start? Yes → **409** *(rule 09)*
5. Hold expired? Yes → **409** *(rule 11)*
6. Already cancelled or expired? Yes → **409** *(rule 02)*
7. Already checked in? Yes → **409** *(rule 15)*
8. Mark `CANCELLED`, release the seats → **200** *(rule 02)*
