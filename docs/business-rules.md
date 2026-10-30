# Business rules

The six rules from the homework specification, and exactly where each one is enforced.

| # | Rule | Response |
|---|---|---|
| 01 | No overbooking | 409 Conflict |
| 02 | Seats stay correct | n/a |
| 03 | Max 4 tickets per booking | 400 Bad Request |
| 04 | Price from the server | n/a |
| 05 | No booking a cancelled event | 409 Conflict |
| 06 | Only your own bookings | 403 Forbidden |

---

## 01: No overbooking

> Booking more tickets than seats left returns **409 Conflict**.

**Enforced in:** `Event.reserveSeats(int)`, called from `BookingService.create`.

```java
if (quantity > availableSeats) {
    throw new ConflictException("Only %d seat(s) left for event %d, requested %d" ...);
}
availableSeats -= quantity;
```

**Concurrency safety.** A check followed by a decrement is a race condition if two requests run at the same time. To prevent it, `BookingService.create` loads the event with `EventRepository.findByIdForUpdate`, which issues `SELECT ... FOR UPDATE`. A second request for the same event waits until the first transaction commits, then sees the updated seat count.

**Last line of defence.** The database constraint `CHECK (available_seats BETWEEN 0 AND total_seats)` makes overselling impossible even if application code is wrong.

**Tested by:** `BookingServiceTest.createRejectsOverbookingWithConflict` and `BookingApiIT.concurrentBookingsNeverOversell`. The concurrency test makes 20 simultaneous bookings for 10 seats. Exactly 10 succeed and 0 seats remain.

## 02: Seats stay correct

> Booking reduces seats; cancelling gives them back.

**Enforced in:**
- `BookingService.create`: `event.reserveSeats(quantity)`
- `BookingService.cancel`: `booking.cancel()` then `event.releaseSeats(quantity)`

**Edge cases handled:**
- **Double cancel.** `Booking.cancel()` throws `ConflictException` (409) if the booking is already `CANCELLED`, so seats are never returned twice. The booking row is also locked (`BookingRepository.findByIdForUpdate`), so two concurrent cancel requests cannot both pass the check.
- **Admin edits the seat count.** `Event.update()` keeps the number of booked seats constant and recomputes `availableSeats`. Reducing `totalSeats` below the number already booked returns 409.
- **Lock ordering.** Every operation locks the event row first. `create` locks only the event row. Cancelling a booking locks its event (`EventRepository.findByBookingIdForUpdate`), then the booking row. Cancelling an event locks the event, then its bookings. Because the order is always event → booking, these operations cannot deadlock.

**Tested by:** `BookingServiceTest.cancelGivesSeatsBack`, `cancelTwiceIsConflictAndDoesNotReleaseSeatsAgain`, and `BookingApiIT.fullBookingFlowFollowsBusinessRules`.

## 03: Max 4 tickets per booking

> Quantity must be 1 to 4, otherwise **400 Bad Request**.

**Enforced in:** `BookingRequest`:

```java
@NotNull
@Min(value = BookingRequest.MIN_TICKETS, message = "must be between 1 and 4")
@Max(value = BookingRequest.MAX_TICKETS, message = "must be between 1 and 4")
Integer quantity
```

Validation runs before the controller method (`@Valid`). The 400 response lists the failing field:

```json
{ "status": 400, "detail": "Validation failed", "errors": { "quantity": "must be between 1 and 4" } }
```

A database constraint `CHECK (quantity BETWEEN 1 AND 4)` backs this up.

> **Note:** The limit applies **per booking**. A customer can make several bookings for the same event. If you need a per-customer limit, add it in `BookingService.create`.

## 04: Price from the server

> Total = event price × quantity. Never trust a price sent by the client.

**Enforced in:**
- `BookingRequest` has **no price fields**. Unknown JSON properties such as `"totalPrice": 0.01` are silently ignored (Spring Boot's default Jackson setting).
- The `Booking` constructor takes the price from the event:

```java
this.unitPrice  = event.getPrice();
this.totalPrice = event.totalPriceFor(quantity);   // price × quantity, BigDecimal
```

The unit price is **stored on the booking** at the moment of purchase. If an admin later changes the event price, existing bookings keep the price the customer actually paid.

Money uses `BigDecimal` in Java and `NUMERIC(10,2)` / `NUMERIC(12,2)` in PostgreSQL, never `double`.

**Tested by:** `BookingApiIT.fullBookingFlowFollowsBusinessRules`. It sends `totalPrice: 0.01` and asserts the response total is `60.00` (3 × 20.00).

## 05: No booking a cancelled event

> If the event status is CANCELLED, return **409 Conflict**.

**Enforced in:** `Event.reserveSeats()` checks the status first:

```java
if (isCancelled()) {
    throw new ConflictException("Event %d is cancelled" ...);
}
```

Events are cancelled by an admin through `POST /api/events/{id}/cancel`. Cancelling an already cancelled event returns 409.

Cancelling an event also cancels every `CONFIRMED` booking for it in the same transaction (`EventService.cancel`), and gives their seats back, so no customer is left holding a "confirmed" ticket for an event that won't happen. Migration `V2__cancel_bookings_of_cancelled_events.sql` applied the same to events cancelled before this rule.

**Tested by:** `BookingServiceTest.createRejectsCancelledEventWithConflict`, `EventServiceTest.cancelEventCancelsItsBookingsAndGivesSeatsBack`, `BookingApiIT.fullBookingFlowFollowsBusinessRules` and `adminAndEventLifecycleRules`.

## 06: Only your own bookings

> Customers can view or cancel only their own bookings, otherwise **403 Forbidden**.

**Enforced in:** `BookingService.checkAccess`, called by `findById` and `cancel`:

```java
if (!currentUser.isAdmin() && !booking.isOwnedBy(currentUser.id())) {
    throw new ForbiddenException("You can only access your own bookings");
}
```

- `GET /api/bookings` (list) is filtered by user ID in the query, so customers only ever see their own bookings.
- **Admins** can view and cancel any booking.
- The caller's identity comes from the verified JWT `sub` claim, **never** from the request body or URL.
- A booking that doesn't exist returns 404. A booking that belongs to someone else returns 403, as the spec requires.

**Tested by:** `BookingServiceTest.customerCannotViewOrCancelSomeoneElsesBooking`, `adminCanViewAnyBooking`, and `BookingApiIT.fullBookingFlowFollowsBusinessRules`.

---

## Rules beyond the brief

These came out of an audit of every operation per role.

| Rule | Response | Enforced in |
|---|---|---|
| Admins can't book; they run events | 403 | `BookingService.create` |
| A new event must start in the future | 400 | `EventRequest` (`@Future`) |
| Once an event has started: no booking, no cancelling a booking, no edit, no cancel | 409 | `Event.requireNotStarted`, called from `reserveSeats`, `update`, `cancel` and `BookingService.cancel` |
| A cancelled event can't be edited | 409 | `Event.update` |
| Cancelling an event cancels its bookings | n/a | `EventService.cancel` (see rule 05) |

"Now" comes from a `Clock` bean (`config/ClockConfig`), so tests use a fixed time.

**Tested by:** `BookingServiceTest.adminCannotBook`, `createRejectsStartedEventWithConflict`, `cancelAfterEventStartedIsConflictAndKeepsTheBooking`, `EventServiceTest` and `BookingApiIT.adminAndEventLifecycleRules`.

## Order of checks when creating a booking

1. JWT valid? No → **401**
2. Body valid, `eventId` present and `quantity` 1–4? No → **400** *(rule 03)*
3. Caller is an admin? Yes → **403**
4. Event exists? No → **404** *(row is now locked)*
5. Event cancelled? Yes → **409** *(rule 05)*
6. Event already started? Yes → **409**
7. Enough seats? No → **409** *(rule 01)*
8. Decrement seats, save booking with the server-computed price → **201** *(rules 02, 04)*
