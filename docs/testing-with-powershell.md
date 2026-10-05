# Testing with PowerShell

A step-by-step manual test of every business rule from **PowerShell**. Works in Windows PowerShell 5.1 and PowerShell 7+.

> Run each block in the **same PowerShell window**. Variables such as `$API` and `$ALICE` exist only in that window, so if you open a new one, start again from step 1.

## 0. Start the app

In a separate PowerShell window (see [development.md](development.md) for details):

```powershell
cd C:\nn\dev_training_backend
.\mvnw.cmd spring-boot:run
```

Wait until you see `Started EventTicketsApplication`. Then use a second PowerShell window for everything below.

## 1. Setup

Paste this whole block once. It stores the API address and defines a helper function, `Invoke-Api`, that:
- sends JSON and adds the `Authorization` header when you pass `-Token`,
- prints the **HTTP status code** for every call, in green for success and yellow for errors,
- returns the response body as an object, **including for errors** (plain `Invoke-RestMethod` throws instead).

```powershell
$API = "http://localhost:8080"

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [Parameter(Mandatory)][string]$Path,
        [object]$Body,
        [string]$Token
    )
    $request = @{ Method = $Method; Uri = "$API$Path"; Headers = @{}; UseBasicParsing = $true }
    if ($Token) { $request.Headers.Authorization = "Bearer $Token" }
    if ($null -ne $Body) {
        $request.ContentType = "application/json"
        $request.Body = $Body | ConvertTo-Json -Compress
    }
    try {
        $response = Invoke-WebRequest @request
        $status = [int]$response.StatusCode
        $content = $response.Content
    }
    catch {
        if (-not $_.Exception.Response) { throw }   # server not reachable
        $status = [int]$_.Exception.Response.StatusCode
        $content = $_.ErrorDetails.Message
        if (-not $content -and $_.Exception.Response -is [System.Net.WebResponse]) {
            # Windows PowerShell 5.1: read the error body from the response stream
            $stream = $_.Exception.Response.GetResponseStream()
            $stream.Position = 0
            $content = (New-Object System.IO.StreamReader($stream)).ReadToEnd()
        }
    }
    if ($content -is [byte[]]) { $content = [System.Text.Encoding]::UTF8.GetString($content) }
    $color = if ($status -lt 400) { "Green" } else { "Yellow" }
    Write-Host "HTTP $status" -ForegroundColor $color
    if ($content) { $content | ConvertFrom-Json }
}
```

Check that the app is up:

```powershell
Invoke-Api -Path /actuator/health
# HTTP 200
# status : UP
```

## 2. Log in as admin and create an event

```powershell
$ADMIN = (Invoke-Api -Method Post -Path /api/auth/login `
    -Body @{ email = "admin@tickets.local"; password = "Admin@12345" }).accessToken
$ADMIN    # should print a long eyJ... token

$EVENT = (Invoke-Api -Method Post -Path /api/events -Token $ADMIN -Body @{
    title      = "Rock Night"
    venue      = "City Arena"
    startsAt   = "2030-06-01T19:00:00Z"
    price      = 20.00
    totalSeats = 5
}).id
"event id = $EVENT"

Invoke-Api -Path /api/events | Format-Table id, title, price, availableSeats, status
```

## 3. Create two customers

Register, then log in. On the first run, `register` returns **201**. If the user already exists from an earlier run, it returns **409**, which you can ignore, because the login that follows works either way.

```powershell
Invoke-Api -Method Post -Path /api/auth/register `
    -Body @{ email = "alice@example.com"; password = "Password123"; fullName = "Alice" } | Out-Null
$ALICE = (Invoke-Api -Method Post -Path /api/auth/login `
    -Body @{ email = "alice@example.com"; password = "Password123" }).accessToken

Invoke-Api -Method Post -Path /api/auth/register `
    -Body @{ email = "bob@example.com"; password = "Password123"; fullName = "Bob" } | Out-Null
$BOB = (Invoke-Api -Method Post -Path /api/auth/login `
    -Body @{ email = "bob@example.com"; password = "Password123" }).accessToken

Invoke-Api -Path /api/auth/me -Token $ALICE
```

## 4. Test the six business rules

See [business-rules.md](business-rules.md) for how each rule is implemented.

### Rule 03: max 4 tickets → 400

```powershell
$r = Invoke-Api -Method Post -Path /api/bookings -Token $ALICE -Body @{ eventId = $EVENT; quantity = 5 }
$r.errors
# HTTP 400
# quantity : must be between 1 and 4
```

### Rule 04: price comes from the server

The fake `totalPrice` in the request is ignored:

```powershell
$booking = Invoke-Api -Method Post -Path /api/bookings -Token $ALICE `
    -Body @{ eventId = $EVENT; quantity = 3; totalPrice = 0.01 }
$booking | Format-List id, quantity, unitPrice, totalPrice, status
$BOOKING = $booking.id
# HTTP 201
# totalPrice : 60.00      (3 × 20.00, not 0.01; PowerShell 7 displays it as 60)
```

### Rule 02: booking reduces seats

```powershell
(Invoke-Api -Path /api/events/$EVENT).availableSeats
# HTTP 200
# 2
```

### Rule 01: no overbooking → 409

```powershell
(Invoke-Api -Method Post -Path /api/bookings -Token $BOB -Body @{ eventId = $EVENT; quantity = 3 }).detail
# HTTP 409
# Only 2 seat(s) left for event 1, requested 3
```

### Rule 06: only your own bookings → 403

```powershell
Invoke-Api -Path /api/bookings/$BOOKING -Token $BOB                   # HTTP 403
Invoke-Api -Method Post -Path /api/bookings/$BOOKING/cancel -Token $BOB   # HTTP 403
Invoke-Api -Path /api/bookings -Token $BOB                            # HTTP 200 (Bob's list doesn't include Alice's booking)
Invoke-Api -Path /api/bookings/$BOOKING -Token $ALICE                 # HTTP 200
```

### Rule 02: cancelling gives seats back, and a second cancel → 409

```powershell
(Invoke-Api -Method Post -Path /api/bookings/$BOOKING/cancel -Token $ALICE).status   # HTTP 200  CANCELLED
(Invoke-Api -Path /api/events/$EVENT).availableSeats                                 # HTTP 200  5
(Invoke-Api -Method Post -Path /api/bookings/$BOOKING/cancel -Token $ALICE).detail   # HTTP 409  Booking ... is already cancelled
```

### Rule 05: booking a cancelled event → 409

```powershell
(Invoke-Api -Method Post -Path /api/events/$EVENT/cancel -Token $ADMIN).status       # HTTP 200  CANCELLED
(Invoke-Api -Method Post -Path /api/bookings -Token $BOB -Body @{ eventId = $EVENT; quantity = 1 }).detail
# HTTP 409
# Event 1 is cancelled
```

## 5. Login and permission checks

```powershell
# no token → 401
Invoke-Api -Path /api/bookings

# wrong password → 401
Invoke-Api -Method Post -Path /api/auth/login -Body @{ email = "alice@example.com"; password = "nope" }

# a customer trying to create an event → 403
Invoke-Api -Method Post -Path /api/events -Token $ALICE -Body @{
    title = "X"; venue = "Y"; startsAt = "2030-01-01T00:00:00Z"; price = 1; totalSeats = 1
}
```

401 and 403 responses from the security layer have an empty body, so only the `HTTP` line is printed.

## Expected results

| Step | Request | Expected |
|---|---|---|
| Rule 03 | book 5 tickets | 400 |
| Rule 04 | book 3 with `totalPrice = 0.01` | 201, `totalPrice` 60.00 |
| Rule 02 | get event | `availableSeats` 2 |
| Rule 01 | Bob books 3 | 409 |
| Rule 06 | Bob views or cancels Alice's booking | 403 |
| Rule 02 | Alice cancels | 200, `availableSeats` back to 5 |
| Rule 02 | Alice cancels again | 409 |
| Rule 05 | book a cancelled event | 409 |
| Auth | no token / wrong password | 401 |
| Auth | customer creates an event | 403 |

> Your event and booking IDs will differ from the examples if you've run the guide before. Every run creates a new event.

## Tips

- **See the full JSON** of any response: `Invoke-Api -Path /api/events/$EVENT | ConvertTo-Json`
- **Tokens expire after 1 hour.** If you start getting 401s, run the login commands from steps 2 and 3 again.
- **Don't use `curl` in Windows PowerShell 5.1.** There, `curl` is an alias for `Invoke-WebRequest`, which takes different options. Use `Invoke-Api` from step 1, or call `curl.exe` explicitly.
- **To start again from an empty database**, stop the app, then run:
  ```powershell
  $env:PGPASSWORD = "postgres"
  psql -h localhost -U postgres -c "DROP DATABASE event_tickets;" -c "CREATE DATABASE event_tickets;"
  ```
  Then start the app again.
- **To run all of these checks automatically**, use `.\mvnw.cmd verify` (see [development.md](development.md#4-tests)).
- **For an IDE-based alternative**, see [`api.http`](../api.http).
