# API reference

Base URL (local): `http://localhost:8080`. All bodies are JSON. Timestamps are ISO-8601 UTC (for example `2030-06-01T19:00:00Z`). Money values are decimal numbers with 2 decimal places.

Authenticated endpoints need this header:

```
Authorization: Bearer <accessToken>
```

Runnable examples for every endpoint are in [`../api.http`](../api.http).

## Endpoint summary

| Method | Path | Access | Success |
|---|---|---|---|
| POST | `/api/auth/register` | Public | 201 |
| POST | `/api/auth/login` | Public | 200 |
| GET | `/api/auth/me` | Authenticated | 200 |
| GET | `/api/events` | Public | 200 |
| GET | `/api/events/{id}` | Public | 200 |
| POST | `/api/events` | ADMIN | 201 |
| PUT | `/api/events/{id}` | ADMIN | 200 |
| POST | `/api/events/{id}/cancel` | ADMIN | 200 |
| POST | `/api/bookings` | Authenticated | 201 |
| GET | `/api/bookings` | Authenticated | 200 |
| GET | `/api/bookings/{id}` | Owner or ADMIN | 200 |
| POST | `/api/bookings/{id}/cancel` | Owner or ADMIN | 200 |
| GET | `/actuator/health` | Public | 200 |

---

## Auth

### `POST /api/auth/register`

Creates a **CUSTOMER** account and logs it in. Nobody can register as an admin.

Request:

```json
{ "email": "alice@example.com", "password": "Password123", "fullName": "Alice" }
```

| Field | Rules |
|---|---|
| `email` | required, valid email, ≤ 255 chars, unique (case-insensitive, stored lowercase) |
| `password` | required, 8–72 chars (BCrypt limit) |
| `fullName` | required, ≤ 100 chars |

Response `201`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-05T15:00:00Z",
  "user": {
    "id": 2,
    "email": "alice@example.com",
    "fullName": "Alice",
    "role": "CUSTOMER",
    "createdAt": "2026-10-05T14:00:00Z"
  }
}
```

Errors: `400` validation failed, `409` email already registered.

### `POST /api/auth/login`

```json
{ "email": "alice@example.com", "password": "Password123" }
```

Response `200`: same shape as register.
Errors: `400` missing fields, `401` invalid email or password. The message is the same for both cases, so it doesn't reveal which emails exist.

### `GET /api/auth/me`

Returns the current user's profile (`UserResponse`). Errors: `401`.

---

## Events

### Event object

```json
{
  "id": 1,
  "title": "Rock Night",
  "description": "Live bands all night",
  "venue": "City Arena",
  "startsAt": "2030-06-01T19:00:00Z",
  "price": 25.00,
  "totalSeats": 100,
  "availableSeats": 98,
  "status": "SCHEDULED"
}
```

`status` is `SCHEDULED` or `CANCELLED`.

### `GET /api/events`

Lists all events, ordered by `startsAt` ascending. Public.

### `GET /api/events/{id}`

Gets one event. Errors: `404`.

### `POST /api/events` (ADMIN)

Request:

```json
{
  "title": "Rock Night",
  "description": "Live bands all night",
  "venue": "City Arena",
  "startsAt": "2030-06-01T19:00:00Z",
  "price": 25.00,
  "totalSeats": 100
}
```

| Field | Rules |
|---|---|
| `title` | required, ≤ 200 chars |
| `description` | optional, ≤ 5000 chars |
| `venue` | required, ≤ 200 chars |
| `startsAt` | required, ISO-8601 instant |
| `price` | required, ≥ 0, max 8 integer digits and 2 decimals |
| `totalSeats` | required, ≥ 1 |

New events start with `availableSeats = totalSeats` and `status = SCHEDULED`.
Response `201` with a `Location: /api/events/{id}` header. Errors: `400`, `401`, `403` (not an admin).

### `PUT /api/events/{id}` (ADMIN)

Same body as create; replaces all fields. Booked seats are preserved:
`availableSeats = newTotalSeats − alreadyBookedSeats`.
Errors: `400`, `404`, `409` if `totalSeats` would drop below the number of seats already booked.

### `POST /api/events/{id}/cancel` (ADMIN)

Sets `status` to `CANCELLED`. New bookings are then rejected with 409. Existing bookings are left unchanged.
Errors: `404`, `409` if the event is already cancelled.

---

## Bookings

### Booking object

```json
{
  "id": 7,
  "eventId": 1,
  "eventTitle": "Rock Night",
  "userId": 2,
  "quantity": 2,
  "unitPrice": 25.00,
  "totalPrice": 50.00,
  "status": "CONFIRMED",
  "createdAt": "2026-10-05T14:05:00Z",
  "cancelledAt": null
}
```

`status` is `CONFIRMED` or `CANCELLED`.

### `POST /api/bookings`

```json
{ "eventId": 1, "quantity": 2 }
```

| Field | Rules |
|---|---|
| `eventId` | required |
| `quantity` | required, 1–4 |

Any price fields in the body are **ignored**. The server computes `totalPrice = event.price × quantity`.

Response `201` with a `Location: /api/bookings/{id}` header.

| Status | When |
|---|---|
| 400 | `quantity` missing or outside 1–4 |
| 401 | no/invalid token |
| 404 | event doesn't exist |
| 409 | event is cancelled, **or** not enough seats left |

### `GET /api/bookings`

- **Customer:** their own bookings, newest first.
- **Admin:** all bookings, newest first.

### `GET /api/bookings/{id}`

Errors: `403` if the booking belongs to another customer, `404` if it doesn't exist.

### `POST /api/bookings/{id}/cancel`

Marks the booking `CANCELLED`, sets `cancelledAt`, and returns the seats to the event.

| Status | When |
|---|---|
| 403 | booking belongs to another customer |
| 404 | booking doesn't exist |
| 409 | booking is already cancelled |

---

## Error format

Errors raised by the application use `application/problem+json` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)):

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "Only 1 seat(s) left for event 1, requested 3",
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
  "errors": { "quantity": "must be between 1 and 4" }
}
```

> A `401` for a missing/invalid token, and a `403` for the wrong role on an admin URL, come from Spring Security's filter chain before any controller runs. These responses have an empty body and a `WWW-Authenticate` header that describes the problem.
