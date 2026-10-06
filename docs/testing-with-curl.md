# Testing with curl (macOS / Linux)

A manual test plan for the cinema booking rules. Run the steps in order, all in the **same terminal window** (zsh or bash).

## Setup

1. Start the app: `docker compose up -d --build` (no JDK needed), or `./mvnw spring-boot:run` if you have JDK 25 installed
2. You need `curl` and `jq`. Both come with macOS 15+. On older macOS: `brew install jq`. On Linux: `sudo apt install curl jq`.
3. Paste this once. `api METHOD PATH [JSON] [TOKEN] [IDEMPOTENCY_KEY]` prints the response body, then the HTTP status:

```sh
API=http://localhost:8090

api() {
  local args=(-s -X "$1" "$API$2" -H "Content-Type: application/json" -w '%{stderr}\nHTTP %{http_code}\n')
  [ -n "$3" ] && args+=(-d "$3")
  [ -n "$4" ] && args+=(-H "Authorization: Bearer $4")
  [ -n "$5" ] && args+=(-H "Idempotency-Key: $5")
  curl "${args[@]}"
}
```

The variables `ADMIN`, `DARA`, `SOKHA`, `MOVIE`, `MOVIE_R18`, `HALL`, `SHOW`, `SHOW_R18`, `BOOKING` and `TICKET` are set by the steps below. Requests without a body pass `''` as the JSON.

---

## Setup data

### 1. Admin login

```sh
ADMIN=$(api POST /api/auth/login '{"email": "admin@tickets.local", "password": "Admin@12345"}' | jq -r .accessToken)
```

✅ **200**

### 2. Create two movies

```sh
MOVIE=$(api POST /api/movies '{"title": "Angkor Legends", "durationMinutes": 120, "ageRating": "G"}' $ADMIN | jq .id)
MOVIE_R18=$(api POST /api/movies '{"title": "Night in Phnom Penh", "durationMinutes": 105, "ageRating": "R18"}' $ADMIN | jq .id)
```

✅ **201**, **201**

### 3. Create a hall

Rows A–E are STANDARD, row F is VIP (1.5 × price), row G is COUPLE sofas (2 × price). The name includes the time, so you can rerun the guide.

```sh
HALL=$(api POST /api/halls '{
  "name": "Koh Pich Hall '$(date +%H%M%S)'",
  "rows": [
    {"row": "A", "seats": 8, "type": "STANDARD"},
    {"row": "B", "seats": 8, "type": "STANDARD"},
    {"row": "C", "seats": 8, "type": "STANDARD"},
    {"row": "D", "seats": 8, "type": "STANDARD"},
    {"row": "E", "seats": 8, "type": "STANDARD"},
    {"row": "F", "seats": 6, "type": "VIP"},
    {"row": "G", "seats": 4, "type": "COUPLE"}
  ]
}' $ADMIN | jq .id)
```

✅ **201**, `totalSeats: 50`

### 4. Schedule two showtimes

7 PM and 10 PM Phnom Penh time (UTC+7) on Khmer New Year. The first ends at 21:15 local: 120 minutes plus 15 minutes of cleaning.

```sh
SHOW=$(api POST /api/showtimes '{"movieId": '$MOVIE', "hallId": '$HALL', "startsAt": "2030-04-14T12:00:00Z", "basePrice": 4.00}' $ADMIN | jq .id)
SHOW_R18=$(api POST /api/showtimes '{"movieId": '$MOVIE_R18', "hallId": '$HALL', "startsAt": "2030-04-14T15:00:00Z", "basePrice": 5.00}' $ADMIN | jq .id)
```

✅ **201**, **201**

### 5. Register and log in Dara (adult)

```sh
api POST /api/auth/register '{"email": "dara@example.com", "password": "Password123", "fullName": "Sok Dara", "dateOfBirth": "1998-03-21"}'
DARA=$(api POST /api/auth/login '{"email": "dara@example.com", "password": "Password123"}' | jq -r .accessToken)
```

✅ **201** (or **409** if Dara already exists), then **200**

### 6. Register and log in Sokha (15 years old)

```sh
api POST /api/auth/register '{"email": "sokha@example.com", "password": "Password123", "fullName": "Chan Sokha", "dateOfBirth": "2015-01-10"}'
SOKHA=$(api POST /api/auth/login '{"email": "sokha@example.com", "password": "Password123"}' | jq -r .accessToken)
```

✅ **201** (or **409**), then **200**

---

## Booking rules

### 7. See the seat map

```sh
api GET /api/showtimes/$SHOW/seats | jq -c '.showtime.availableSeats, [.seats[] | select(.row == "F") | {label, price, available}]'
```

✅ **200**, `50`, and row F at `6.00` each, all `available: true`

### 8. Rule 03: more than 4 seats in one booking is rejected

```sh
api POST /api/bookings '{"showtimeId": '$SHOW', "seats": ["C1", "C2", "C3", "C4", "C5"]}' $DARA
```

✅ **400**, `"errors":{"seats":"must be between 1 and 4 seats"}`

### 9. Rule 04: seats are held, price comes from the server

```sh
BOOKING=$(api POST /api/bookings '{"showtimeId": '$SHOW', "seats": ["A3", "A4", "F1"], "totalPrice": 0.01}' $DARA | jq .id)
api GET /api/bookings/$BOOKING '' $DARA | jq -c '{status, totalPrice, holdExpiresAt, seats: [.seats[].label]}'
```

✅ **201**, then **200** with `"status":"HELD"`, `"totalPrice":14.00` (4.00 + 4.00 + 6.00 VIP, the fake price is ignored), and a `holdExpiresAt` 10 minutes from now

### 10. Rule 01: a seat can't be sold twice

```sh
api POST /api/bookings '{"showtimeId": '$SHOW', "seats": ["A4", "A5"]}' $SOKHA
```

✅ **409**, `"detail":"Seat(s) already taken: A4"`

### 11. Rule 13: no single empty seat between taken seats

A6 would leave A5 empty between A4 and A6.

```sh
api POST /api/bookings '{"showtimeId": '$SHOW', "seats": ["A6"]}' $SOKHA
```

✅ **409**, `"detail":"This choice would leave a single empty seat at A5. …"`

### 12. Rule 06: Sokha can't see or pay for Dara's booking

```sh
api GET /api/bookings/$BOOKING '' $SOKHA
api POST /api/bookings/$BOOKING/pay '' $SOKHA
```

✅ **403**, **403**

### 13. Rule 11: pay within 10 minutes to get a ticket

```sh
TICKET=$(api POST /api/bookings/$BOOKING/pay '' $DARA | jq -r .ticketCode)
echo $TICKET
api POST /api/bookings/$BOOKING/pay '' $DARA
```

✅ **200** and a code like `K7QW-M2XP`, then **409**, `"detail":"Booking … is CONFIRMED, only HELD bookings can be paid"`

### 14. Rule 12: a retried request doesn't book twice

Sending the same `Idempotency-Key` again returns the first booking instead of creating a second one. The key includes the showtime id so it's new every run: reusing a key for a different booking request is rejected with **409**.

```sh
api POST /api/bookings '{"showtimeId": '$SHOW', "seats": ["B1", "B2"]}' $SOKHA sokha-$SHOW | jq .id
api POST /api/bookings '{"showtimeId": '$SHOW', "seats": ["B1", "B2"]}' $SOKHA sokha-$SHOW | jq .id
```

✅ **201** twice, with the **same id**

### 15. Rule 07: max 8 seats per customer per showtime

Sokha already holds B1–B2. Four more is 6, then three more would be 9.

```sh
api POST /api/bookings '{"showtimeId": '$SHOW', "seats": ["B3", "B4", "B5", "B6"]}' $SOKHA
api POST /api/bookings '{"showtimeId": '$SHOW', "seats": ["C1", "C2", "C3"]}' $SOKHA
```

✅ **201**, then **409**, `"detail":"You can book at most 8 seats for showtime …, you already have 6"`

### 16. Rule 14: age rating

Sokha is 15, and *Night in Phnom Penh* is R18.

```sh
api POST /api/bookings '{"showtimeId": '$SHOW_R18', "seats": ["D1"]}' $SOKHA
```

✅ **403**, `"detail":"'Night in Phnom Penh' is rated R18: you must be at least 18 on the day of the showtime"`. If Sokha was registered earlier without a date of birth, the message asks for one instead.

### 17. Rule 15: check-in opens 1 hour before the start

```sh
api POST /api/tickets/$TICKET/check-in '' $ADMIN
```

✅ **409**, `"detail":"Check-in for ticket … opens at 2030-04-14T11:00:00Z"`

### 18. Rule 02: cancelling gives the seats back, exactly once

```sh
api POST /api/bookings/$BOOKING/cancel '' $DARA | jq -c '{status, cancelledAt}'
api GET /api/showtimes/$SHOW | jq .availableSeats
api POST /api/bookings/$BOOKING/cancel '' $DARA
```

✅ **200** `"status":"CANCELLED"`, then **200** `44` (50 − Sokha's 6), then **409**, `"detail":"Booking … is already cancelled"`

---

## Showtime rules

### 19. Rule 16: no overlapping showtimes in a hall

The 7 PM show uses the hall until 21:15 local (14:15 UTC).

```sh
api POST /api/showtimes '{"movieId": '$MOVIE', "hallId": '$HALL', "startsAt": "2030-04-14T14:00:00Z", "basePrice": 4.00}' $ADMIN
```

✅ **409**, `"detail":"Koh Pich Hall … already has a showtime in that time slot (movie length + cleaning time)"`

### 20. Rule 17: a showtime with bookings can't move

```sh
api PUT /api/showtimes/$SHOW '{"movieId": '$MOVIE', "hallId": '$HALL', "startsAt": "2030-04-15T12:00:00Z", "basePrice": 4.00}' $ADMIN
```

✅ **409**, `"detail":"Showtime … already has bookings: its movie, hall and start time can't change. …"`

### 21. Rules 10 and 05: cancelling a showtime cancels its bookings; no new bookings

```sh
api POST /api/showtimes/$SHOW/cancel '' $ADMIN | jq -c '{status, availableSeats}'
api GET /api/bookings '' $SOKHA | jq -c '[.[] | select(.showtimeId == '$SHOW') | .status]'
api POST /api/bookings '{"showtimeId": '$SHOW', "seats": ["E1"]}' $DARA
```

✅ **200** `{"status":"CANCELLED","availableSeats":0}`, then **200** `["CANCELLED","CANCELLED"]`, then **409**, `"detail":"Showtime … is cancelled"`

---

## Security

### 22. No token

```sh
api GET /api/bookings
```

✅ **401**

### 23. Wrong password

```sh
api POST /api/auth/login '{"email": "dara@example.com", "password": "wrong-password"}'
```

✅ **401**, `"detail":"Invalid email or password"`

### 24. Customer can't add movies

```sh
api POST /api/movies '{"title": "Not Allowed", "durationMinutes": 90, "ageRating": "G"}' $DARA
```

✅ **403**

### 25. Duplicate registration

```sh
api POST /api/auth/register '{"email": "dara@example.com", "password": "Password123", "fullName": "Sok Dara"}'
```

✅ **409**, `"detail":"Email is already registered"`

---

## Notes

- **Time-based rules** (hold expiry after 10 minutes, cancel deadline 2 hours before the start, check-in window, sales closing at the start) are covered by the automated tests, which use a test clock. To try hold expiry by hand, start the app with a short hold, for example `BOOKING_HOLD_DURATION=1m docker compose up -d`, hold a seat, wait a minute, then try to pay (**409**).
- Rule numbers match [business-rules.md](business-rules.md).
- Tokens expire after 1 hour. If you start getting 401s, repeat the login commands.
- Each run creates a new hall and showtimes, so you can run the guide again without resetting the database. Dara and Sokha are registered once; later runs get **409** at registration, which is fine.
- 401 and 403 responses from the security layer have an empty body, so only the `HTTP` line is printed.
- To pretty-print a response, add `| jq`.
- `'{"showtimeId": '$SHOW', …}'` closes the single quotes around `$SHOW` so the shell fills in its value.
- To start from an empty database: `docker compose down -v`, then `docker compose up -d --build`.
- The full API reference is in [api.md](api.md).
