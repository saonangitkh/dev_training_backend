# Testing with curl

A step-by-step manual test of every business rule using `curl`.

> **Shell:** Use **Git Bash** (or any bash shell), not PowerShell, where the JSON quoting breaks. These commands don't need `jq`: tokens and IDs are extracted with `sed`.

## 0. Start the app

In a separate terminal, create the database once, then start the app (see [development.md](development.md) for details):

```bash
psql -U postgres -c "CREATE DATABASE event_tickets;"
export DB_PASSWORD='your-postgres-password'
./mvnw spring-boot:run
```

Wait until you see `Started EventTicketsApplication`. Then use a second Git Bash window for everything below.

## 1. Setup

```bash
API=http://localhost:8080
JSON='Content-Type: application/json'
W='\nHTTP %{http_code}\n'      # prints the status code after each response

curl -s -w "$W" $API/actuator/health
# {"status":"UP"}  HTTP 200
```

## 2. Log in as admin and create an event

```bash
ADMIN=$(curl -s -X POST $API/api/auth/login -H "$JSON" \
  -d '{"email":"admin@tickets.local","password":"Admin@12345"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
echo $ADMIN    # should print a long eyJ... token

EVENT=$(curl -s -X POST $API/api/events -H "$JSON" -H "Authorization: Bearer $ADMIN" \
  -d '{"title":"Rock Night","venue":"City Arena","startsAt":"2030-06-01T19:00:00Z","price":20.00,"totalSeats":5}' \
  | sed -E 's/^\{"id":([0-9]+).*/\1/')
echo "event id = $EVENT"

curl -s -w "$W" $API/api/events
```

## 3. Register two customers

```bash
ALICE=$(curl -s -X POST $API/api/auth/register -H "$JSON" \
  -d '{"email":"alice@example.com","password":"Password123","fullName":"Alice"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

BOB=$(curl -s -X POST $API/api/auth/register -H "$JSON" \
  -d '{"email":"bob@example.com","password":"Password123","fullName":"Bob"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s -w "$W" $API/api/auth/me -H "Authorization: Bearer $ALICE"
```

> On a second run, `register` returns 409 because the email already exists. Use `/api/auth/login` with the same body instead.

## 4. Test the six business rules

See [business-rules.md](business-rules.md) for how each rule is implemented.

### Rule 03: max 4 tickets → 400

```bash
curl -s -w "$W" -X POST $API/api/bookings -H "$JSON" -H "Authorization: Bearer $ALICE" \
  -d "{\"eventId\":$EVENT,\"quantity\":5}"
# "errors":{"quantity":"must be between 1 and 4"}   HTTP 400
```

### Rule 04: price comes from the server

The fake `totalPrice` in the request is ignored:

```bash
BOOKING=$(curl -s -X POST $API/api/bookings -H "$JSON" -H "Authorization: Bearer $ALICE" \
  -d "{\"eventId\":$EVENT,\"quantity\":3,\"totalPrice\":0.01}" \
  | tee /dev/stderr | sed -E 's/^\{"id":([0-9]+).*/\1/')
# ..."totalPrice":60.00...   (3 × 20.00, not 0.01)
```

### Rule 02: booking reduces seats

```bash
curl -s $API/api/events/$EVENT
# "availableSeats":2
```

### Rule 01: no overbooking → 409

```bash
curl -s -w "$W" -X POST $API/api/bookings -H "$JSON" -H "Authorization: Bearer $BOB" \
  -d "{\"eventId\":$EVENT,\"quantity\":3}"
# "detail":"Only 2 seat(s) left for event 1, requested 3"   HTTP 409
```

### Rule 06: only your own bookings → 403

```bash
curl -s -w "$W" $API/api/bookings/$BOOKING -H "Authorization: Bearer $BOB"                  # HTTP 403
curl -s -w "$W" -X POST $API/api/bookings/$BOOKING/cancel -H "Authorization: Bearer $BOB"   # HTTP 403
curl -s -w "$W" $API/api/bookings -H "Authorization: Bearer $BOB"                           # []  HTTP 200
curl -s -w "$W" $API/api/bookings/$BOOKING -H "Authorization: Bearer $ALICE"                # HTTP 200
```

### Rule 02: cancelling gives seats back, and a second cancel → 409

```bash
curl -s -w "$W" -X POST $API/api/bookings/$BOOKING/cancel -H "Authorization: Bearer $ALICE"  # "status":"CANCELLED"  HTTP 200
curl -s $API/api/events/$EVENT                                                               # "availableSeats":5
curl -s -w "$W" -X POST $API/api/bookings/$BOOKING/cancel -H "Authorization: Bearer $ALICE"  # HTTP 409
```

### Rule 05: booking a cancelled event → 409

```bash
curl -s -w "$W" -X POST $API/api/events/$EVENT/cancel -H "Authorization: Bearer $ADMIN"      # "status":"CANCELLED"
curl -s -w "$W" -X POST $API/api/bookings -H "$JSON" -H "Authorization: Bearer $BOB" \
  -d "{\"eventId\":$EVENT,\"quantity\":1}"
# "detail":"Event 1 is cancelled"   HTTP 409
```

## 5. Login and permission checks

```bash
# no token → 401
curl -s -w "$W" $API/api/bookings

# wrong password → 401
curl -s -w "$W" -X POST $API/api/auth/login -H "$JSON" \
  -d '{"email":"alice@example.com","password":"nope"}'

# a customer trying to create an event → 403
curl -s -w "$W" -X POST $API/api/events -H "$JSON" -H "Authorization: Bearer $ALICE" \
  -d '{"title":"X","venue":"Y","startsAt":"2030-01-01T00:00:00Z","price":1,"totalSeats":1}'

# full headers (useful for 401/403, which come back with an empty body)
curl -i $API/api/bookings
```

## Expected results

| Step | Request | Expected |
|---|---|---|
| Rule 03 | book 5 tickets | 400 |
| Rule 04 | book 3 with `totalPrice: 0.01` | 201, `totalPrice` 60.00 |
| Rule 02 | get event | `availableSeats` 2 |
| Rule 01 | Bob books 3 | 409 |
| Rule 06 | Bob views or cancels Alice's booking | 403 |
| Rule 02 | Alice cancels | 200, `availableSeats` back to 5 |
| Rule 02 | Alice cancels again | 409 |
| Rule 05 | book a cancelled event | 409 |
| Auth | no token / wrong password | 401 |
| Auth | customer creates an event | 403 |

## Tips

- Tokens expire after 1 hour. If you start getting 401s, run the login commands again to get fresh `ADMIN`, `ALICE` and `BOB` tokens.
- Variables like `$ALICE` only exist in the current terminal window.
- To start again from an empty database, run `psql -U postgres -c "DROP DATABASE event_tickets;" -c "CREATE DATABASE event_tickets;"` and restart the app.
- To run all of these checks automatically, use `./mvnw verify` (see [development.md](development.md#4-tests)).
- For an IDE-based alternative, see [`api.http`](../api.http).
