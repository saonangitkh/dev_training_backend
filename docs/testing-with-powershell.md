# Testing with PowerShell

A manual test plan for the six business rules. Run the steps in order, all in the **same PowerShell window**.

## Setup

1. Start the app: `docker compose up -d --build` (no JDK needed), or `.\mvnw.cmd spring-boot:run` if you have JDK 25 installed
2. Open **PowerShell 7** (`pwsh`). Windows PowerShell 5.1 doesn't support `-SkipHttpErrorCheck`; install 7 with `winget install Microsoft.PowerShell`.
3. Paste this once. `api` prints the HTTP status and returns the response body, including for errors:

```powershell
$API = "http://localhost:8090"

function api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $json = if ($null -ne $Body) { $Body | ConvertTo-Json }
    $response = Invoke-RestMethod -Method $Method -Uri "$API$Path" -Headers $headers -Body $json `
        -ContentType "application/json" -SkipHttpErrorCheck -StatusCodeVariable status
    Write-Host "HTTP $status"
    $response
}
```

The variables `$ADMIN`, `$DARA`, `$SOKHA`, `$EVENT` and `$BOOKING` are set by the steps below.

---

## Setup data

### 1. Admin login

```powershell
$ADMIN = (api POST /api/auth/login @{ email = "admin@tickets.local"; password = "Admin@12345" }).accessToken
```

✅ **200**

### 2. Create event

```powershell
$EVENT = (api POST /api/events -Token $ADMIN -Body @{
    title       = "Khmer New Year Concert"
    description = "Live Khmer music and Apsara dance, 7 PM Phnom Penh time"
    venue       = "Koh Pich Theatre, Phnom Penh"
    startsAt    = "2030-04-14T12:00:00Z"
    price       = 20.00
    totalSeats  = 5
}).id
```

✅ **201**

### 3. Register Dara

```powershell
api POST /api/auth/register @{ email = "dara@example.com"; password = "Password123"; fullName = "Sok Dara" }
```

✅ **201**. If Dara already exists, you get **409**. Continue to the login step.

### 4. Login Dara

```powershell
$DARA = (api POST /api/auth/login @{ email = "dara@example.com"; password = "Password123" }).accessToken
```

✅ **200**

### 5. Register Sokha

```powershell
api POST /api/auth/register @{ email = "sokha@example.com"; password = "Password123"; fullName = "Chan Sokha" }
```

✅ **201**. If Sokha already exists, you get **409**.

### 6. Login Sokha

```powershell
$SOKHA = (api POST /api/auth/login @{ email = "sokha@example.com"; password = "Password123" }).accessToken
```

✅ **200**

---

## Business rules

### 7. Rule 03: more than 4 tickets is rejected

```powershell
api POST /api/bookings @{ eventId = $EVENT; quantity = 5 } -Token $DARA
```

✅ **400**, `errors.quantity: "must be between 1 and 4"`

### 8. Rule 04: price comes from the server

```powershell
$BOOKING = (api POST /api/bookings @{ eventId = $EVENT; quantity = 3; totalPrice = 0.01 } -Token $DARA).id
api GET /api/bookings/$BOOKING -Token $DARA
```

✅ **201**, then **200** with `totalPrice: 60` (3 × 20.00, the fake price is ignored)

### 9. Rule 02: booking reduces seats

```powershell
api GET /api/events/$EVENT
```

✅ **200**, `availableSeats: 2`

### 10. Rule 01: overbooking is rejected

```powershell
api POST /api/bookings @{ eventId = $EVENT; quantity = 3 } -Token $SOKHA
```

✅ **409**, `detail: "Only 2 seat(s) left for event …, requested 3"`

### 11. Rule 06: Sokha can't view Dara's booking

```powershell
api GET /api/bookings/$BOOKING -Token $SOKHA
```

✅ **403**

### 12. Rule 06: Sokha can't cancel Dara's booking

```powershell
api POST /api/bookings/$BOOKING/cancel -Token $SOKHA
```

✅ **403**

### 13. Rule 06: Sokha's list doesn't include Dara's booking

```powershell
api GET /api/bookings -Token $SOKHA
```

✅ **200**. `$BOOKING` is not in the list.

### 14. Rule 02: cancelling gives seats back

```powershell
api POST /api/bookings/$BOOKING/cancel -Token $DARA
api GET /api/events/$EVENT
```

✅ **200**, `status: CANCELLED`, then **200**, `availableSeats: 5`

### 15. Rule 02: cancelling twice is rejected

```powershell
api POST /api/bookings/$BOOKING/cancel -Token $DARA
```

✅ **409**, `detail: "Booking … is already cancelled"`

### 16. Rule 05: booking a cancelled event is rejected

```powershell
api POST /api/events/$EVENT/cancel -Token $ADMIN
api POST /api/bookings @{ eventId = $EVENT; quantity = 1 } -Token $SOKHA
```

✅ **200**, `status: CANCELLED`, then **409**, `detail: "Event … is cancelled"`

---

## Security

### 17. No token

```powershell
api GET /api/bookings
```

✅ **401**

### 18. Wrong password

```powershell
api POST /api/auth/login @{ email = "dara@example.com"; password = "wrong-password" }
```

✅ **401**, `detail: "Invalid email or password"`

### 19. Customer can't create events

```powershell
api POST /api/events -Token $DARA -Body @{
    title      = "Angkor Wat Sunrise Tour"
    venue      = "Angkor Wat, Siem Reap"
    startsAt   = "2030-01-01T23:00:00Z"
    price      = 10.00
    totalSeats = 10
}
```

✅ **403**

### 20. Duplicate registration

```powershell
api POST /api/auth/register @{ email = "dara@example.com"; password = "Password123"; fullName = "Sok Dara" }
```

✅ **409**, `detail: "Email is already registered"`

---

## Notes

- Tokens expire after 1 hour. If you start getting 401s, repeat the login steps.
- Steps 2–16 need a new event each time you run the plan. Repeat step 2.
- 401 and 403 responses from the security layer have an empty body, so only the `HTTP` line is printed.
- To see a full response as JSON, add `| ConvertTo-Json`.
- To start from an empty database: `docker compose down -v`, then `docker compose up -d --build`.
- The full API reference is in [api.md](api.md).
