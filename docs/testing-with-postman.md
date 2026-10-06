# Testing with Postman

A manual test plan for the six business rules. Run the steps in order.

## Setup

1. Start the app: `docker compose up -d --build` (no JDK needed), or `.\mvnw.cmd spring-boot:run` if you have JDK 25 installed
2. Create a Postman environment:

| Variable | Value |
|---|---|
| `baseUrl` | `http://localhost:8090` |
| `adminToken`, `daraToken`, `sokhaToken` | `accessToken` from each login response |
| `eventId`, `bookingId` | `id` from the create responses |

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

### 2. Create event

**POST** `{{baseUrl}}/api/events`
Auth: Bearer `{{adminToken}}`

```json
{
  "title": "Khmer New Year Concert",
  "description": "Live Khmer music and Apsara dance, 7 PM Phnom Penh time",
  "venue": "Koh Pich Theatre, Phnom Penh",
  "startsAt": "2030-04-14T12:00:00Z",
  "price": 20.00,
  "totalSeats": 5
}
```

✅ **201**, `availableSeats: 5`, `status: SCHEDULED`. Save `id` → `eventId`.

### 3. Register Dara

**POST** `{{baseUrl}}/api/auth/register`
Auth: none

```json
{
  "email": "dara@example.com",
  "password": "Password123",
  "fullName": "Sok Dara"
}
```

✅ **201**. If Dara already exists, you get **409**. Continue to the login step.

### 4. Login Dara

**POST** `{{baseUrl}}/api/auth/login`
Auth: none

```json
{
  "email": "dara@example.com",
  "password": "Password123"
}
```

✅ **200**. Save `accessToken` → `daraToken`.

### 5. Register Sokha

**POST** `{{baseUrl}}/api/auth/register`
Auth: none

```json
{
  "email": "sokha@example.com",
  "password": "Password123",
  "fullName": "Chan Sokha"
}
```

✅ **201**. If Sokha already exists, you get **409**.

### 6. Login Sokha

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

## Business rules

### 7. Rule 03: more than 4 tickets is rejected

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{daraToken}}`

```json
{
  "eventId": {{eventId}},
  "quantity": 5
}
```

✅ **400**, `errors.quantity: "must be between 1 and 4"`

### 8. Rule 04: price comes from the server

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{daraToken}}`

```json
{
  "eventId": {{eventId}},
  "quantity": 3,
  "totalPrice": 0.01
}
```

✅ **201**, `totalPrice: 60.00` (3 × 20.00, the fake price is ignored). Save `id` → `bookingId`.

### 9. Rule 02: booking reduces seats

**GET** `{{baseUrl}}/api/events/{{eventId}}`
Auth: none

✅ **200**, `availableSeats: 2`

### 10. Rule 01: overbooking is rejected

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{sokhaToken}}`

```json
{
  "eventId": {{eventId}},
  "quantity": 3
}
```

✅ **409**, `detail: "Only 2 seat(s) left for event …, requested 3"`

### 11. Rule 06: Sokha can't view Dara's booking

**GET** `{{baseUrl}}/api/bookings/{{bookingId}}`
Auth: Bearer `{{sokhaToken}}`

✅ **403**

### 12. Rule 06: Sokha can't cancel Dara's booking

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/cancel`
Auth: Bearer `{{sokhaToken}}`

✅ **403**

### 13. Rule 06: Sokha's list doesn't include Dara's booking

**GET** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{sokhaToken}}`

✅ **200**. `bookingId` is not in the list.

### 14. Rule 02: cancelling gives seats back

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/cancel`
Auth: Bearer `{{daraToken}}`

✅ **200**, `status: CANCELLED`

**GET** `{{baseUrl}}/api/events/{{eventId}}`

✅ **200**, `availableSeats: 5`

### 15. Rule 02: cancelling twice is rejected

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/cancel`
Auth: Bearer `{{daraToken}}`

✅ **409**, `detail: "Booking … is already cancelled"`

### 16. Rule 05: booking a cancelled event is rejected

First cancel the event:

**POST** `{{baseUrl}}/api/events/{{eventId}}/cancel`
Auth: Bearer `{{adminToken}}`

✅ **200**, `status: CANCELLED`

Then try to book it:

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{sokhaToken}}`

```json
{
  "eventId": {{eventId}},
  "quantity": 1
}
```

✅ **409**, `detail: "Event … is cancelled"`

---

## Security

### 17. No token

**GET** `{{baseUrl}}/api/bookings`
Auth: none

✅ **401**

### 18. Wrong password

**POST** `{{baseUrl}}/api/auth/login`
Auth: none

```json
{
  "email": "dara@example.com",
  "password": "wrong-password"
}
```

✅ **401**, `detail: "Invalid email or password"`

### 19. Customer can't create events

**POST** `{{baseUrl}}/api/events`
Auth: Bearer `{{daraToken}}`

```json
{
  "title": "Angkor Wat Sunrise Tour",
  "venue": "Angkor Wat, Siem Reap",
  "startsAt": "2030-01-01T23:00:00Z",
  "price": 10.00,
  "totalSeats": 10
}
```

✅ **403**

### 20. Duplicate registration

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

- Tokens expire after 1 hour. If you start getting 401s, repeat the login steps.
- Steps 2–16 need a new event each time you run the plan. Repeat step 2 and update `eventId`.
- 401 and 403 responses from the security layer have an empty body. Other errors return JSON with a `detail` message.
- The full API reference is in [api.md](api.md).
