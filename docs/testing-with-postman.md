# Testing with Postman

A manual test plan for the six business rules. Run the steps in order.

## Setup

1. Start the app: `.\mvnw.cmd spring-boot:run`
2. Create a Postman environment:

| Variable | Value |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `adminToken`, `aliceToken`, `bobToken` | `accessToken` from each login response |
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
  "title": "Rock Night",
  "description": "Live bands all night",
  "venue": "City Arena",
  "startsAt": "2030-06-01T19:00:00Z",
  "price": 20.00,
  "totalSeats": 5
}
```

✅ **201**, `availableSeats: 5`, `status: SCHEDULED`. Save `id` → `eventId`.

### 3. Register Alice

**POST** `{{baseUrl}}/api/auth/register`
Auth: none

```json
{
  "email": "alice@example.com",
  "password": "Password123",
  "fullName": "Alice"
}
```

✅ **201**. If Alice already exists, you get **409**. Continue to the login step.

### 4. Login Alice

**POST** `{{baseUrl}}/api/auth/login`
Auth: none

```json
{
  "email": "alice@example.com",
  "password": "Password123"
}
```

✅ **200**. Save `accessToken` → `aliceToken`.

### 5. Register Bob

**POST** `{{baseUrl}}/api/auth/register`
Auth: none

```json
{
  "email": "bob@example.com",
  "password": "Password123",
  "fullName": "Bob"
}
```

✅ **201**. If Bob already exists, you get **409**.

### 6. Login Bob

**POST** `{{baseUrl}}/api/auth/login`
Auth: none

```json
{
  "email": "bob@example.com",
  "password": "Password123"
}
```

✅ **200**. Save `accessToken` → `bobToken`.

---

## Business rules

### 7. Rule 03: more than 4 tickets is rejected

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{aliceToken}}`

```json
{
  "eventId": {{eventId}},
  "quantity": 5
}
```

✅ **400**, `errors.quantity: "must be between 1 and 4"`

### 8. Rule 04: price comes from the server

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{aliceToken}}`

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
Auth: Bearer `{{bobToken}}`

```json
{
  "eventId": {{eventId}},
  "quantity": 3
}
```

✅ **409**, `detail: "Only 2 seat(s) left for event …, requested 3"`

### 11. Rule 06: Bob can't view Alice's booking

**GET** `{{baseUrl}}/api/bookings/{{bookingId}}`
Auth: Bearer `{{bobToken}}`

✅ **403**

### 12. Rule 06: Bob can't cancel Alice's booking

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/cancel`
Auth: Bearer `{{bobToken}}`

✅ **403**

### 13. Rule 06: Bob's list doesn't include Alice's booking

**GET** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{bobToken}}`

✅ **200**. `bookingId` is not in the list.

### 14. Rule 02: cancelling gives seats back

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/cancel`
Auth: Bearer `{{aliceToken}}`

✅ **200**, `status: CANCELLED`

**GET** `{{baseUrl}}/api/events/{{eventId}}`

✅ **200**, `availableSeats: 5`

### 15. Rule 02: cancelling twice is rejected

**POST** `{{baseUrl}}/api/bookings/{{bookingId}}/cancel`
Auth: Bearer `{{aliceToken}}`

✅ **409**, `detail: "Booking … is already cancelled"`

### 16. Rule 05: booking a cancelled event is rejected

First cancel the event:

**POST** `{{baseUrl}}/api/events/{{eventId}}/cancel`
Auth: Bearer `{{adminToken}}`

✅ **200**, `status: CANCELLED`

Then try to book it:

**POST** `{{baseUrl}}/api/bookings`
Auth: Bearer `{{bobToken}}`

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
  "email": "alice@example.com",
  "password": "wrong-password"
}
```

✅ **401**, `detail: "Invalid email or password"`

### 19. Customer can't create events

**POST** `{{baseUrl}}/api/events`
Auth: Bearer `{{aliceToken}}`

```json
{
  "title": "Not Allowed",
  "venue": "Anywhere",
  "startsAt": "2030-01-01T19:00:00Z",
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
  "email": "alice@example.com",
  "password": "Password123",
  "fullName": "Alice"
}
```

✅ **409**, `detail: "Email is already registered"`

---

## Notes

- Tokens expire after 1 hour. If you start getting 401s, repeat the login steps.
- Steps 2–16 need a new event each time you run the plan. Repeat step 2 and update `eventId`.
- 401 and 403 responses from the security layer have an empty body. Other errors return JSON with a `detail` message.
- The full API reference is in [api.md](api.md).
