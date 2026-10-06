# Testing with Postman

A manual test plan for the cinema booking rules. Run the steps in order.

## Setup

1. Start the app: `docker compose up -d --build` (no JDK needed), or `.\mvnw.cmd spring-boot:run` if you have JDK 25 installed
2. Create a Postman environment:

| Variable | Value |
|---|---|
| `baseUrl` | `http://localhost:8090` |
| `adminToken`, `daraToken`, `sokhaToken` | `accessToken` from each login response |
| `movieId`, `movieR18Id` | `id` from step 2 |
| `hallId` | `id` from step 3 |
| `showtimeId`, `showtimeR18Id` | `id` from step 4 |
| `bookingId` | `id` from step 9 |
| `ticketCode` | `ticketCode` from step 13 |

**Auth: Bearer** means Authorization tab → Bearer Token → the token variable shown. Every request with a body also sends `Content-Type: application/json`.

---

## Setup data

### 1. Admin login

**POST** `{{baseUrl}}/api/auth/login`
Auth: none

```json
{
  "email": "admin@tickets.local",
  "password": "Admin@12345"
}
```

✅ **200**. Save `accessToken` → `adminToken`.

### 2. Create two movies

**POST** `{{baseUrl}}/api/movies`
Auth: Bearer `{{adminToken}}`

```json
{
  "title": "Angkor Legends",
  "durationMinutes": 120,
  "ageRating": "G"
}
```

✅ **201**. Save `id` → `movieId`.

Send the same request again with this body:

```json
{
  "title": "Night in Phnom Penh",
  "durationMinutes": 105,
  "ageRating": "R18"
}
```

✅ **201**. Save `id` → `movieR18Id`.

### 3. Create a hall

Rows A–E are STANDARD, row F is VIP (1.5 × price), row G is COUPLE sofas (2 × price). `{{$timestamp}}` puts the current time in the name, so you can rerun the guide.

**POST** `{{baseUrl}}/api/halls`
Auth: Bearer `{{adminToken}}`

```json
{
  "name": "Koh Pich Hall {{$timestamp}}",
  "rows": [
    {"row": "A", "seats": 8, "type": "STANDARD"},
    {"row": "B", "seats": 8, "type": "STANDARD"},
    {"row": "C", "seats": 8, "type": "STANDARD"},
    {"row": "D", "seats": 8, "type": "STANDARD"},
    {"row": "E", "seats": 8, "type": "STANDARD"},
    {"row": "F", "seats": 6, "type": "VIP"},
    {"row": "G", "seats": 4, "type": "COUPLE"}
  ]
}
```

✅ **201**, `totalSeats: 50`. Save `id` → `hallId`.

### 4. Schedule two showtimes

7 PM and 10 PM Phnom Penh time (UTC+7) on Khmer New Year. The first ends at 21:15 local: 120 minutes plus 15 minutes of cleaning.

**POST** `{{baseUrl}}/api/showtimes`
Auth: Bearer `{{adminToken}}`

```json
{
  "movieId": {{movieId}},
  "hallId": {{hallId}},
  "startsAt": "2030-04-14T12:00:00Z",
  "basePrice": 4.00
}
```

✅ **201**. Save `id` → `showtimeId`.

Send the same request again with this body:

```json
{
  "movieId": {{movieR18Id}},
  "hallId": {{hallId}},
  "startsAt": "2030-04-14T15:00:00Z",
  "basePrice": 5.00
}
```

✅ **201**. Save `id` → `showtimeR18Id`.

### 5. Register and log in Dara (adult)

**POST** `{{baseUrl}}/api/auth/register`
Auth: none

```json
{
  "email": "dara@example.com",
  "password": "Password123",
  "fullName": "Sok Dara",
  "dateOfBirth": "1998-03-21"
}
```

✅ **201** (or **409** if Dara already exists)

**POST** `{{baseUrl}}/api/auth/login`
Auth: none

```json
{
  "email": "dara@example.com",
  "password": "Password123"
}
```

✅ **200**. Save `accessToken` → `daraToken`.

### 6. Register and log in Sokha (15 years old)

**POST** `{{baseUrl}}/api/auth/register`
Auth: none

```json
{
  "email": "sokha@example.com",
  "password": "Password123",
  "fullName": "Chan Sokha",
  "dateOfBirth": "2015-01-10"
}
```

✅ **201** (or **409**)

**POST** `{{baseUrl}}/api/auth/login`
Auth: none

```json
{
  "email": "sokha@example.com",
  "password": "Password123"
}
```

✅ **200**. Save `accessToken` → `sokhaToken`.

---

## Booking rules

### 7. See the seat map

**GET** `{{baseUrl}}/api/showtimes/{{showtimeId}}/seats`
Auth: none

✅ **200**, `showtime.availableSeats: 50`, and the row F seats (F1–F6) in `seats` have `price: 6.00`, all `available: true`

### 8. Rule 03: more than 4 seats in one booking is rejected

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{daraToken}}`

```json
{
  "showtimeId": {{showtimeId}},
  "seats": ["C1", "C2", "C3", "C4", "C5"]
}
```

✅ **400**, `errors.seats: "must be between 1 and 4 seats"`

### 9. Rule 04: seats are held, price comes from the server

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{daraToken}}`

```json
{
  "showtimeId": {{showtimeId}},
  "seats": ["A3", "A4", "F1"],
  "totalPrice": 0.01
}
```

✅ **201**. Save `id` → `bookingId`.

**GET** `{{baseUrl}}/api/bookings/{{bookingId}}`
Auth: Bearer `{{daraToken}}`

✅ **200** with `status: HELD`, `totalPrice: 14.00` (4.00 + 4.00 + 6.00 VIP, the fake price is ignored), seats A3, A4, F1, and a `holdExpiresAt` 10 minutes from now

### 10. Rule 01: a seat can't be sold twice

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{sokhaToken}}`

```json
{
  "showtimeId": {{showtimeId}},
  "seats": ["A4", "A5"]
}
```

✅ **409**, `detail: "Seat(s) already taken: A4"`

### 11. Rule 13: no single empty seat between taken seats

A6 would leave A5 empty between A4 and A6.

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{sokhaToken}}`

```json
{
  "showtimeId": {{showtimeId}},
  "seats": ["A6"]
}
```

✅ **409**, `detail: "This choice would leave a single empty seat at A5. …"`

### 12. Rule 06: Sokha can't see or pay for Dara's booking

**GET** `{{baseUrl}}/api/bookings/{{bookingId}}`
Auth: Bearer `{{sokhaToken}}`

✅ **403**

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/pay`
Auth: Bearer `{{sokhaToken}}`

✅ **403**

### 13. Rule 11: pay within 10 minutes to get a ticket

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/pay`
Auth: Bearer `{{daraToken}}`

✅ **200** and a `ticketCode` like `K7QW-M2XP`. Save `ticketCode` → `ticketCode`.

Send the same request again.

✅ **409**, `detail: "Booking … is CONFIRMED, only HELD bookings can be paid"`

### 14. Rule 12: a retried request doesn't book twice

Sending the same `Idempotency-Key` again returns the first booking instead of creating a second one. The key includes the showtime id so it's new every run: reusing a key for a different booking request is rejected with **409**.

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{sokhaToken}}`
Headers tab → `Idempotency-Key: sokha-{{showtimeId}}`

```json
{
  "showtimeId": {{showtimeId}},
  "seats": ["B1", "B2"]
}
```

Send it twice.

✅ **201** twice, with the **same id**

### 15. Rule 07: max 8 seats per customer per showtime

Sokha already holds B1–B2. Four more is 6, then three more would be 9.

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{sokhaToken}}`

```json
{
  "showtimeId": {{showtimeId}},
  "seats": ["B3", "B4", "B5", "B6"]
}
```

✅ **201**

Send the same request again with this body:

```json
{
  "showtimeId": {{showtimeId}},
  "seats": ["C1", "C2", "C3"]
}
```

✅ **409**, `detail: "You can book at most 8 seats for showtime …, you already have 6"`

### 16. Rule 14: age rating

Sokha is 15, and *Night in Phnom Penh* is R18.

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{sokhaToken}}`

```json
{
  "showtimeId": {{showtimeR18Id}},
  "seats": ["D1"]
}
```

✅ **403**, `detail: "'Night in Phnom Penh' is rated R18: you must be at least 18 on the day of the showtime"`. If Sokha was registered earlier without a date of birth, the message asks for one instead.

### 17. Rule 15: check-in opens 1 hour before the start

**POST** `{{baseUrl}}/api/tickets/{{ticketCode}}/check-in`
Auth: Bearer `{{adminToken}}`

✅ **409**, `detail: "Check-in for ticket … opens at 2030-04-14T11:00:00Z"`

### 18. Rule 02: cancelling gives the seats back, exactly once

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/cancel`
Auth: Bearer `{{daraToken}}`

✅ **200**, `status: CANCELLED`, with a `cancelledAt` time

**GET** `{{baseUrl}}/api/showtimes/{{showtimeId}}`
Auth: none

✅ **200**, `availableSeats: 44` (50 − Sokha's 6)

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/cancel`
Auth: Bearer `{{daraToken}}`

✅ **409**, `detail: "Booking … is already cancelled"`

---

## Showtime rules

### 19. Rule 16: no overlapping showtimes in a hall

The 7 PM show uses the hall until 21:15 local (14:15 UTC).

**POST** `{{baseUrl}}/api/showtimes`
Auth: Bearer `{{adminToken}}`

```json
{
  "movieId": {{movieId}},
  "hallId": {{hallId}},
  "startsAt": "2030-04-14T14:00:00Z",
  "basePrice": 4.00
}
```

✅ **409**, `detail: "Koh Pich Hall … already has a showtime in that time slot (movie length + cleaning time)"`

### 20. Rule 17: a showtime with bookings can't move

**PUT** `{{baseUrl}}/api/showtimes/{{showtimeId}}`
Auth: Bearer `{{adminToken}}`

```json
{
  "movieId": {{movieId}},
  "hallId": {{hallId}},
  "startsAt": "2030-04-15T12:00:00Z",
  "basePrice": 4.00
}
```

✅ **409**, `detail: "Showtime … already has bookings: its movie, hall and start time can't change. …"`

### 21. Rules 10 and 05: cancelling a showtime cancels its bookings; no new bookings

**POST** `{{baseUrl}}/api/showtimes/{{showtimeId}}/cancel`
Auth: Bearer `{{adminToken}}`

✅ **200**, `status: CANCELLED`, `availableSeats: 0`

**GET** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{sokhaToken}}`

✅ **200**. Both of Sokha's bookings with this `showtimeId` (B1–B2 and B3–B6) have `status: CANCELLED`.

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{daraToken}}`

```json
{
  "showtimeId": {{showtimeId}},
  "seats": ["E1"]
}
```

✅ **409**, `detail: "Showtime … is cancelled"`

---

## Security

### 22. No token

**GET** `{{baseUrl}}/api/bookings`
Auth: none

✅ **401**

### 23. Wrong password

**POST** `{{baseUrl}}/api/auth/login`
Auth: none

```json
{
  "email": "dara@example.com",
  "password": "wrong-password"
}
```

✅ **401**, `detail: "Invalid email or password"`

### 24. Customer can't add movies

**POST** `{{baseUrl}}/api/movies`
Auth: Bearer `{{daraToken}}`

```json
{
  "title": "Not Allowed",
  "durationMinutes": 90,
  "ageRating": "G"
}
```

✅ **403**

### 25. Duplicate registration

**POST** `{{baseUrl}}/api/auth/register`
Auth: none

```json
{
  "email": "dara@example.com",
  "password": "Password123",
  "fullName": "Sok Dara"
}
```

✅ **409**, `detail: "Email is already registered"`

---

## Notes

- **Time-based rules** (hold expiry after 10 minutes, cancel deadline 2 hours before the start, check-in window, sales closing at the start) are covered by the automated tests, which use a test clock. To try hold expiry by hand, start the app with a short hold, for example `BOOKING_HOLD_DURATION=1m docker compose up -d` (macOS / Linux) or `$env:BOOKING_HOLD_DURATION="1m"; docker compose up -d` (PowerShell), hold a seat, wait a minute, then try to pay (**409**).
- Rule numbers match [business-rules.md](business-rules.md).
- Tokens expire after 1 hour. If you start getting 401s, repeat the login requests.
- Each run creates a new hall and showtimes, so you can run the guide again without resetting the database. Remember to save the new ids. Dara and Sokha are registered once; later runs get **409** at registration, which is fine.
- 401 and 403 responses from the security layer have an empty body. Other errors return JSON with a `detail` message.
- If you reuse the step 14 request tab for later bookings, remove the `Idempotency-Key` header first, so they aren't treated as retries of step 14.
- To start from an empty database: `docker compose down -v`, then `docker compose up -d --build`.
- The full API reference is in [api.md](api.md).
