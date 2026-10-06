# API reference

Base URL (local): `http://localhost:8090`. All bodies are JSON. Timestamps are ISO-8601 UTC (for example `2030-04-14T12:00:00Z`, which is 7 PM in Phnom Penh). Dates of birth are ISO dates (`1998-03-21`). Money values are decimal numbers with 2 decimal places.

Authenticated endpoints need this header:

```
Authorization: Bearer <accessToken>
```

Runnable examples for every endpoint are in [`../api.http`](../api.http). The examples below use the same sample data as [testing with curl](testing-with-curl.md). Rule numbers (for example "rule 13") refer to [business rules](business-rules.md).

## Endpoint summary

| Method | Path | Access | Success |
|---|---|---|---|
| POST | `/api/auth/register` | Public | 201 |
| POST | `/api/auth/login` | Public | 200 |
| GET | `/api/auth/me` | Authenticated | 200 |
| GET | `/api/movies` | Public | 200 |
| GET | `/api/movies/{id}` | Public | 200 |
| POST | `/api/movies` | ADMIN | 201 |
| PUT | `/api/movies/{id}` | ADMIN | 200 |
| GET | `/api/halls` | Public | 200 |
| GET | `/api/halls/{id}` | Public | 200 |
| POST | `/api/halls` | ADMIN | 201 |
| GET | `/api/showtimes?movieId={id}` | Public | 200 |
| GET | `/api/showtimes/{id}` | Public | 200 |
| GET | `/api/showtimes/{id}/seats` | Public | 200 |
| POST | `/api/showtimes` | ADMIN | 201 |
| PUT | `/api/showtimes/{id}` | ADMIN | 200 |
| POST | `/api/showtimes/{id}/cancel` | ADMIN | 200 |
| POST | `/api/bookings` | Authenticated | 201 |
| GET | `/api/bookings` | Authenticated | 200 |
| GET | `/api/bookings/{id}` | Owner or ADMIN | 200 |
| POST | `/api/bookings/{id}/pay` | Owner or ADMIN | 200 |
| POST | `/api/bookings/{id}/cancel` | Owner or ADMIN | 200 |
| POST | `/api/tickets/{code}/check-in` | ADMIN | 200 |
| GET | `/actuator/health` | Public | 200 |

`GET` on movies, halls and showtimes is public. Every other method on those paths, and everything under `/api/tickets`, needs the ADMIN role. Admin-only URLs return `401` without a token and `403` with a customer token.

---

## Auth

### `POST /api/auth/register`

Creates a **CUSTOMER** account and logs it in. Nobody can register as an admin.

Request:

```json
{ "email": "dara@example.com", "password": "Password123", "fullName": "Sok Dara", "dateOfBirth": "1998-03-21" }
```

| Field | Rules |
|---|---|
| `email` | required, valid email, ≤ 255 chars, unique (case-insensitive, stored lowercase) |
| `password` | required, 8–72 chars (BCrypt limit) |
| `fullName` | required, ≤ 100 chars |
| `dateOfBirth` | optional, ISO date in the past. Needed to book PG13 and R18 movies (rule 14) |

Response `201`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-06T10:00:00Z",
  "user": {
    "id": 2,
    "email": "dara@example.com",
    "fullName": "Sok Dara",
    "role": "CUSTOMER",
    "dateOfBirth": "1998-03-21",
    "createdAt": "2026-10-06T09:00:00Z"
  }
}
```

`dateOfBirth` is `null` if it wasn't given.

| Status | When |
|---|---|
| 400 | validation failed (missing or invalid field, `dateOfBirth` not in the past) |
| 409 | `Email is already registered` |

### `POST /api/auth/login`

```json
{ "email": "dara@example.com", "password": "Password123" }
```

Response `200`: same shape as register.

| Status | When |
|---|---|
| 400 | missing fields |
| 401 | `Invalid email or password`. The message is the same for both cases, so it doesn't reveal which emails exist |

### `GET /api/auth/me`

Returns the current user's profile (the `user` object above).

| Status | When |
|---|---|
| 401 | no/invalid token |
| 404 | the user in the token no longer exists |

---

## Movies

### Movie object

```json
{
  "id": 1,
  "title": "Angkor Legends",
  "description": "An adventure among the temples of Angkor",
  "durationMinutes": 120,
  "ageRating": "G"
}
```

`ageRating` is `G` (anyone), `PG13` (13+) or `R18` (18+).

### `GET /api/movies`

Lists all movies, ordered by title. Public.

### `GET /api/movies/{id}`

Gets one movie.

| Status | When |
|---|---|
| 404 | `Movie {id} not found` |

### `POST /api/movies` (ADMIN)

Request:

```json
{ "title": "Night in Phnom Penh", "durationMinutes": 105, "ageRating": "R18" }
```

| Field | Rules |
|---|---|
| `title` | required, ≤ 200 chars |
| `description` | optional, ≤ 5000 chars |
| `durationMinutes` | required, 1–600 |
| `ageRating` | required, `G`, `PG13` or `R18` |

Response `201` with a `Location: /api/movies/{id}` header.

| Status | When |
|---|---|
| 400 | validation failed |
| 401 | no/invalid token |
| 403 | not an admin |

### `PUT /api/movies/{id}` (ADMIN)

Same body as create; replaces all fields. Showtimes that are already scheduled keep the end time computed when they were created.

| Status | When |
|---|---|
| 400 | validation failed |
| 401 | no/invalid token |
| 403 | not an admin |
| 404 | `Movie {id} not found` |

---

## Halls

### Hall object

```json
{
  "id": 1,
  "name": "Koh Pich Hall",
  "totalSeats": 50,
  "seats": [
    { "id": 1, "label": "A1", "row": "A", "number": 1, "type": "STANDARD" },
    { "id": 2, "label": "A2", "row": "A", "number": 2, "type": "STANDARD" },
    "…",
    { "id": 50, "label": "G4", "row": "G", "number": 4, "type": "COUPLE" }
  ]
}
```

Seats are ordered by row, then number. `type` is `STANDARD`, `VIP` or `COUPLE` (a two-person sofa sold as one seat). A hall's layout can't be changed after it is created.

### `GET /api/halls`

Lists all halls with their seats, ordered by name. Public.

### `GET /api/halls/{id}`

Gets one hall with its seats.

| Status | When |
|---|---|
| 404 | `Hall {id} not found` |

### `POST /api/halls` (ADMIN)

Rows are listed front row first. Seats in each row are numbered from 1.

```json
{
  "name": "Koh Pich Hall",
  "rows": [
    { "row": "A", "seats": 8, "type": "STANDARD" },
    { "row": "B", "seats": 8, "type": "STANDARD" },
    { "row": "C", "seats": 8, "type": "STANDARD" },
    { "row": "D", "seats": 8, "type": "STANDARD" },
    { "row": "E", "seats": 8, "type": "STANDARD" },
    { "row": "F", "seats": 6, "type": "VIP" },
    { "row": "G", "seats": 4, "type": "COUPLE" }
  ]
}
```

| Field | Rules |
|---|---|
| `name` | required, ≤ 100 chars, unique (case-insensitive) |
| `rows` | required, 1–26 rows |
| `rows[].row` | required, 1–3 capital letters, each row only once |
| `rows[].seats` | required, 1–50 |
| `rows[].type` | required, `STANDARD`, `VIP` or `COUPLE` |

Response `201` with a `Location: /api/halls/{id}` header and the hall object (`totalSeats: 50` for the example).

| Status | When |
|---|---|
| 400 | validation failed, or `Row A is defined twice` |
| 401 | no/invalid token |
| 403 | not an admin |
| 409 | `Hall 'Koh Pich Hall' already exists` |

---

## Showtimes

### Showtime object

```json
{
  "id": 1,
  "movieId": 1,
  "movieTitle": "Angkor Legends",
  "ageRating": "G",
  "hallId": 1,
  "hallName": "Koh Pich Hall",
  "startsAt": "2030-04-14T12:00:00Z",
  "endsAt": "2030-04-14T14:15:00Z",
  "basePrice": 4.00,
  "status": "SCHEDULED",
  "totalSeats": 50,
  "availableSeats": 47
}
```

- `endsAt` = `startsAt` + movie length + cleaning time (15 minutes by default). The hall is busy until then (rule 16).
- `basePrice` is the price of a STANDARD seat. VIP seats cost 1.5 ×, COUPLE seats 2 × (rule 04).
- `status` is `SCHEDULED` or `CANCELLED`. A cancelled showtime has `availableSeats: 0`.
- `availableSeats` counts seats that can still be booked: not paid for and not in an unexpired hold. It is 0 once the showtime is cancelled or has started.

### `GET /api/showtimes`

Lists upcoming showtimes (ones that haven't **ended** yet), soonest first. Public. Optional query parameter `movieId` lists only that movie's showtimes, for example `GET /api/showtimes?movieId=1`. An unknown `movieId` returns an empty list. Cancelled showtimes that haven't ended are included, with `status: CANCELLED`.

### `GET /api/showtimes/{id}`

Gets one showtime.

| Status | When |
|---|---|
| 404 | `Showtime {id} not found` |

### `GET /api/showtimes/{id}/seats`

The seat map: the showtime object plus every seat in the hall with its price and whether it can be booked right now. Public.

```json
{
  "showtime": { "id": 1, "movieTitle": "Angkor Legends", "…": "…", "totalSeats": 50, "availableSeats": 47 },
  "seats": [
    { "label": "A1", "row": "A", "number": 1, "type": "STANDARD", "price": 4.00, "available": true },
    { "label": "A3", "row": "A", "number": 3, "type": "STANDARD", "price": 4.00, "available": false },
    { "label": "F1", "row": "F", "number": 1, "type": "VIP", "price": 6.00, "available": false },
    { "label": "G1", "row": "G", "number": 1, "type": "COUPLE", "price": 8.00, "available": true }
  ]
}
```

(Shortened: the real response lists all 50 seats, ordered by row, then number.) Every seat is `available: false` once the showtime has started or is cancelled.

| Status | When |
|---|---|
| 404 | `Showtime {id} not found` |

### `POST /api/showtimes` (ADMIN)

```json
{ "movieId": 1, "hallId": 1, "startsAt": "2030-04-14T12:00:00Z", "basePrice": 4.00 }
```

| Field | Rules |
|---|---|
| `movieId` | required, existing movie |
| `hallId` | required, existing hall |
| `startsAt` | required, ISO-8601 instant, must be in the future (rule 08) |
| `basePrice` | required, ≥ 0, max 8 integer digits and 2 decimals |

New showtimes start with `status = SCHEDULED`. Response `201` with a `Location: /api/showtimes/{id}` header and the showtime object.

| Status | When |
|---|---|
| 400 | validation failed (for example `startsAt` not in the future) |
| 401 | no/invalid token |
| 403 | not an admin |
| 404 | `Movie {id} not found`, `Hall {id} not found` |
| 409 | `Koh Pich Hall already has a showtime in that time slot (movie length + cleaning time)` (rule 16). Only scheduled showtimes count; a PostgreSQL exclusion constraint also catches two admins scheduling at the same moment |

### `PUT /api/showtimes/{id}` (ADMIN)

Same body as create; replaces all fields. `endsAt` is recomputed only when the movie or start time changes; a price-only change keeps it. Once any seat is held or sold, only `basePrice` can change. Existing bookings keep the price they were sold at.

| Status | When |
|---|---|
| 400 | validation failed. `startsAt` must still be in the future, so a showtime that has started can't be edited |
| 401 | no/invalid token |
| 403 | not an admin |
| 404 | `Showtime {id} not found`, `Movie {id} not found`, `Hall {id} not found` |
| 409 | the showtime is cancelled; it has bookings and the movie, hall or start time would change (`Showtime 1 already has bookings: its movie, hall and start time can't change. …`, rule 17); or the new time slot overlaps another showtime in the hall (rule 16) |

### `POST /api/showtimes/{id}/cancel` (ADMIN)

Sets `status` to `CANCELLED` and cancels every `HELD` and `CONFIRMED` booking for the showtime (holds that already ran out become `EXPIRED` instead) (their `cancelledAt` is set), so all their seats are released (rule 10). New bookings are then rejected with 409 (rule 05). Response `200` with the showtime object (`availableSeats: 0`).

| Status | When |
|---|---|
| 401 | no/invalid token |
| 403 | not an admin |
| 404 | `Showtime {id} not found` |
| 409 | `Showtime {id} is already cancelled` |

---

## Bookings

### Booking object

```json
{
  "id": 1,
  "showtimeId": 1,
  "movieTitle": "Angkor Legends",
  "hallName": "Koh Pich Hall",
  "startsAt": "2030-04-14T12:00:00Z",
  "userId": 2,
  "seats": [
    { "label": "A3", "type": "STANDARD", "price": 4.00 },
    { "label": "A4", "type": "STANDARD", "price": 4.00 },
    { "label": "F1", "type": "VIP", "price": 6.00 }
  ],
  "quantity": 3,
  "totalPrice": 14.00,
  "status": "HELD",
  "holdExpiresAt": "2026-10-06T09:10:00Z",
  "ticketCode": null,
  "createdAt": "2026-10-06T09:00:00Z",
  "confirmedAt": null,
  "cancelledAt": null,
  "checkedInAt": null
}
```

`status` is one of:

| Status | Meaning |
|---|---|
| `HELD` | seats are held until `holdExpiresAt` (10 minutes by default) and must be paid |
| `CONFIRMED` | paid; `ticketCode` and `confirmedAt` are set |
| `CANCELLED` | cancelled by the customer, an admin, or because the showtime was cancelled; `cancelledAt` is set and the seats are free |
| `EXPIRED` | the hold ran out before payment; the seats are free. A hold past `holdExpiresAt` is shown as `EXPIRED` straight away, even before the background job (every minute) updates the row |

Seats are listed by row, then number. Each seat keeps the price it was sold at.

### `POST /api/bookings`

Holds the chosen seats. Pay within the hold time with `POST /api/bookings/{id}/pay`.

```json
{ "showtimeId": 1, "seats": ["A3", "A4", "F1"] }
```

| Field / header | Rules |
|---|---|
| `showtimeId` | required |
| `seats` | required, 1–4 seat labels (rule 03). Case and surrounding spaces are ignored (`" f1"` = `F1`) |
| `Idempotency-Key` header | optional, 1–100 characters. Sending the same key again with the same showtime and seats returns the first booking instead of creating a new one; the same key with a different showtime or different seats → 409 (rule 12) |

Any price fields in the body are **ignored**. The server prices each seat as `basePrice × 1.00` (STANDARD), `× 1.50` (VIP) or `× 2.00` (COUPLE) and adds them up (rule 04). For the example: 4.00 + 4.00 + 6.00 = `14.00`.

Response `201` with a `Location: /api/bookings/{id}` header and the booking object with `status: HELD`. A repeated request with the same `Idempotency-Key` also returns `201` with the first booking (in its current status).

| Status | When |
|---|---|
| 400 | validation failed (`showtimeId` missing, `seats` missing, empty, more than 4 → `"errors":{"seats":"must be between 1 and 4 seats"}`, or a blank label); `Idempotency-Key must be 1-100 characters`; `Seat A3 is listed twice`; `Unknown seat(s) in this hall: Z9` |
| 401 | no/invalid token |
| 403 | age rating (rule 14): `'Night in Phnom Penh' is rated R18: add your date of birth to book it`, or `… you must be at least 18 on the day of the showtime`. Age is counted on the show date in the cinema's time zone (Asia/Phnom_Penh) |
| 404 | `Showtime {id} not found` |
| 409 | `Showtime {id} is cancelled` (rule 05); `Idempotency key was already used for a different booking request` (rule 12); `Showtime {id} has already started, sales are closed` (rule 08); `You can book at most 8 seats for showtime 1, you already have 6` (rule 07); `Seat(s) already taken: A4` (rule 01); `This choice would leave a single empty seat at A5. Choose seats next to each other, or leave at least two seats free` (rule 13); `Idempotency key was already used for a different booking request` (same key, different showtime); `Seats or idempotency key already in use, please try again` (lost a race with a concurrent request) |

### `GET /api/bookings`

- **Customer:** their own bookings, newest first.
- **Admin:** all bookings, newest first.

| Status | When |
|---|---|
| 401 | no/invalid token |

### `GET /api/bookings/{id}`

| Status | When |
|---|---|
| 401 | no/invalid token |
| 403 | `You can only access your own bookings` (rule 06) |
| 404 | `Booking {id} not found` |

### `POST /api/bookings/{id}/pay`

Simulated payment: confirms a `HELD` booking, sets `confirmedAt` and issues a ticket code such as `K7QW-M2XP` (rule 11).

Response `200`:

```json
{
  "id": 1,
  "…": "…",
  "totalPrice": 14.00,
  "status": "CONFIRMED",
  "holdExpiresAt": "2026-10-06T09:10:00Z",
  "ticketCode": "K7QW-M2XP",
  "confirmedAt": "2026-10-06T09:03:00Z",
  "cancelledAt": null,
  "checkedInAt": null
}
```

| Status | When |
|---|---|
| 401 | no/invalid token |
| 403 | `You can only access your own bookings` (rule 06) |
| 404 | `Booking {id} not found` |
| 409 | `Showtime {id} has already started, sales are closed`; `Booking 1 hold expired at …, its seats were released`; `Booking 1 is CONFIRMED, only HELD bookings can be paid` (already paid, cancelled or expired) |

### `POST /api/bookings/{id}/cancel`

Marks the booking `CANCELLED`, sets `cancelledAt`, and releases its seats (rule 02). A held booking can always be cancelled. A paid booking can be cancelled by the customer only until 2 hours before the start; admins can cancel at any time (rule 09).

| Status | When |
|---|---|
| 401 | no/invalid token |
| 403 | `You can only access your own bookings` (rule 06) |
| 404 | `Booking {id} not found` |
| 409 | `Booking 1 is already cancelled` / `… already expired`; `Booking 1 hold expired at …, its seats were released`; `Booking 1 is already checked in`; `Paid bookings can only be cancelled until 2 hour(s) before the showtime starts` (customers only) |

---

## Tickets

### `POST /api/tickets/{code}/check-in` (ADMIN)

Used by cinema staff at the door. Marks the ticket as used and sets `checkedInAt` (rule 15). The code is not case-sensitive. Check-in is open from 1 hour before `startsAt` until `endsAt`, and each ticket can be checked in once.

```
POST /api/tickets/K7QW-M2XP/check-in
```

Response `200` with the booking object (`status: CONFIRMED`, `checkedInAt` set).

| Status | When |
|---|---|
| 401 | no/invalid token |
| 403 | not an admin |
| 404 | `Ticket K7QW-M2XP not found` |
| 409 | `Check-in for ticket K7QW-M2XP opens at 2030-04-14T11:00:00Z`; `Showtime {id} has ended`; `Ticket K7QW-M2XP is not valid: booking is CANCELLED`; `Ticket K7QW-M2XP was already checked in at …` |

---

## Error format

Errors raised by the application use `application/problem+json` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)):

| Status | Exception | Typical cause |
|---|---|---|
| 400 | `BadRequestException`, bean validation | invalid input |
| 401 | `BadCredentialsException` | wrong email or password at login |
| 403 | `ForbiddenException` | someone else's booking, age rating |
| 404 | `NotFoundException` | unknown id or ticket code |
| 409 | `ConflictException` | a business rule blocks the request |

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "Seat(s) already taken: A4",
  "instance": "/api/bookings"
}
```

Validation errors add an `errors` map:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/bookings",
  "errors": { "seats": "must be between 1 and 4 seats" }
}
```

> A `401` for a missing/invalid token, and a `403` for the wrong role on an admin URL, come from Spring Security's filter chain before any controller runs. These responses have an empty body and a `WWW-Authenticate` header that describes the problem.
