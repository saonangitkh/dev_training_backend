# Testing with curl (macOS / Linux)

A manual test plan for the six business rules. Run the steps in order, all in the **same terminal window** (zsh or bash).

## Setup

1. Start the app: `docker compose up -d --build` (no JDK needed), or `./mvnw spring-boot:run` if you have JDK 25 installed
2. You need `curl` and `jq`. Both come with macOS 15+. On older macOS: `brew install jq`. On Linux: `sudo apt install curl jq`.
3. Paste this once. `api METHOD PATH [JSON] [TOKEN]` prints the response body, then the HTTP status:

```sh
API=http://localhost:8090

api() {
  local args=(-s -X "$1" "$API$2" -H "Content-Type: application/json" -w '%{stderr}\nHTTP %{http_code}\n')
  [ -n "$3" ] && args+=(-d "$3")
  [ -n "$4" ] && args+=(-H "Authorization: Bearer $4")
  curl "${args[@]}"
}
```

The variables `ADMIN`, `DARA`, `SOKHA`, `EVENT` and `BOOKING` are set by the steps below. Requests without a body pass `''` as the JSON.

---

## Setup data

### 1. Admin login

```sh
ADMIN=$(api POST /api/auth/login '{"email": "admin@tickets.local", "password": "Admin@12345"}' | jq -r .accessToken)
```

✅ **200**

### 2. Create event

```sh
EVENT=$(api POST /api/events '{
  "title": "Khmer New Year Concert",
  "description": "Live Khmer music and Apsara dance, 7 PM Phnom Penh time",
  "venue": "Koh Pich Theatre, Phnom Penh",
  "startsAt": "2030-04-14T12:00:00Z",
  "price": 20.00,
  "totalSeats": 5
}' $ADMIN | jq .id)
```

✅ **201**

### 3. Register Dara

```sh
api POST /api/auth/register '{"email": "dara@example.com", "password": "Password123", "fullName": "Sok Dara"}'
```

✅ **201**. If Dara already exists, you get **409**. Continue to the login step.

### 4. Login Dara

```sh
DARA=$(api POST /api/auth/login '{"email": "dara@example.com", "password": "Password123"}' | jq -r .accessToken)
```

✅ **200**

### 5. Register Sokha

```sh
api POST /api/auth/register '{"email": "sokha@example.com", "password": "Password123", "fullName": "Chan Sokha"}'
```

✅ **201**. If Sokha already exists, you get **409**.

### 6. Login Sokha

```sh
SOKHA=$(api POST /api/auth/login '{"email": "sokha@example.com", "password": "Password123"}' | jq -r .accessToken)
```

✅ **200**

---

## Business rules

### 7. Rule 03: more than 4 tickets is rejected

```sh
api POST /api/bookings '{"eventId": '$EVENT', "quantity": 5}' $DARA
```

✅ **400**, `"errors":{"quantity":"must be between 1 and 4"}`

### 8. Rule 04: price comes from the server

```sh
BOOKING=$(api POST /api/bookings '{"eventId": '$EVENT', "quantity": 3, "totalPrice": 0.01}' $DARA | jq .id)
api GET /api/bookings/$BOOKING '' $DARA
```

✅ **201**, then **200** with `"totalPrice":60.00` (3 × 20.00, the fake price is ignored)

### 9. Rule 02: booking reduces seats

```sh
api GET /api/events/$EVENT
```

✅ **200**, `"availableSeats":2`

### 10. Rule 01: overbooking is rejected

```sh
api POST /api/bookings '{"eventId": '$EVENT', "quantity": 3}' $SOKHA
```

✅ **409**, `"detail":"Only 2 seat(s) left for event …, requested 3"`

### 11. Rule 06: Sokha can't view Dara's booking

```sh
api GET /api/bookings/$BOOKING '' $SOKHA
```

✅ **403**

### 12. Rule 06: Sokha can't cancel Dara's booking

```sh
api POST /api/bookings/$BOOKING/cancel '' $SOKHA
```

✅ **403**

### 13. Rule 06: Sokha's list doesn't include Dara's booking

```sh
api GET /api/bookings '' $SOKHA
```

✅ **200**. `$BOOKING` is not in the list.

### 14. Rule 02: cancelling gives seats back

```sh
api POST /api/bookings/$BOOKING/cancel '' $DARA
api GET /api/events/$EVENT
```

✅ **200**, `"status":"CANCELLED"`, then **200**, `"availableSeats":5`

### 15. Rule 02: cancelling twice is rejected

```sh
api POST /api/bookings/$BOOKING/cancel '' $DARA
```

✅ **409**, `"detail":"Booking … is already cancelled"`

### 16. Rule 05: booking a cancelled event is rejected

```sh
api POST /api/events/$EVENT/cancel '' $ADMIN
api POST /api/bookings '{"eventId": '$EVENT', "quantity": 1}' $SOKHA
```

✅ **200**, `"status":"CANCELLED"`, then **409**, `"detail":"Event … is cancelled"`

---

## Security

### 17. No token

```sh
api GET /api/bookings
```

✅ **401**

### 18. Wrong password

```sh
api POST /api/auth/login '{"email": "dara@example.com", "password": "wrong-password"}'
```

✅ **401**, `"detail":"Invalid email or password"`

### 19. Customer can't create events

```sh
api POST /api/events '{
  "title": "Angkor Wat Sunrise Tour",
  "venue": "Angkor Wat, Siem Reap",
  "startsAt": "2030-01-01T23:00:00Z",
  "price": 10.00,
  "totalSeats": 10
}' $DARA
```

✅ **403**

### 20. Duplicate registration

```sh
api POST /api/auth/register '{"email": "dara@example.com", "password": "Password123", "fullName": "Sok Dara"}'
```

✅ **409**, `"detail":"Email is already registered"`

---

## Notes

- Tokens expire after 1 hour. If you start getting 401s, repeat the login steps.
- Steps 2–16 need a new event each time you run the plan. Repeat step 2.
- 401 and 403 responses from the security layer have an empty body, so only the `HTTP` line is printed.
- To pretty-print a response, add `| jq`, e.g. `api GET /api/events/$EVENT | jq`.
- `'{"eventId": '$EVENT', …}'` closes the single quotes around `$EVENT` so the shell fills in its value.
- To start from an empty database: `docker compose down -v`, then `docker compose up -d --build`.
- The full API reference is in [api.md](api.md).
