# Cinema Tickets API: Documentation

A REST API for booking cinema seats. Admins add movies and halls and schedule showtimes. Customers register, log in with a JWT, pick 1–4 seats on a seat map, hold them for 10 minutes and pay to get a ticket code. Staff check the ticket in at the door.

| Document | What it covers |
|---|---|
| [Requirements](requirements.md) | Scope, roles, numbered functional & non-functional requirements, assumptions |
| [Architecture](architecture.md) | Tech stack, layers, package layout, request flow |
| [Business rules](business-rules.md) | The 17 booking and showtime rules and exactly how each is enforced |
| [API reference](api.md) | Every endpoint with request/response examples and error codes |
| [Database](database.md) | Schema, constraints, migrations, locking strategy |
| [Security](security.md) | Authentication, JWT format, roles, authorization rules |
| [Development guide](development.md) | Local setup, configuration, testing, coding conventions |
| [Testing with curl](testing-with-curl.md) | Step-by-step manual test of every rule from a macOS / Linux terminal (zsh or bash) |
| [Testing with PowerShell](testing-with-powershell.md) | The same kind of manual test from PowerShell on Windows |
| [Testing with Postman](testing-with-postman.md) | Endpoints, payloads and a step-by-step manual test plan |

## At a glance

- **Stack:** Java 25, Spring Boot 4.1, Spring Security (OAuth2 resource server, HS256 JWT), Spring Data JPA / Hibernate, PostgreSQL, Flyway, Maven
- **Roles:** `CUSTOMER` (self-registered) and `ADMIN` (created from configuration on startup; manages movies, halls, showtimes and checks tickets in)
- **Booking flow:** seat map → hold seats (`HELD`, 10 minutes) → pay (`CONFIRMED`, ticket code) → check in at the door
- **Errors:** RFC 9457 `application/problem+json`
- **Base URL (local):** `http://localhost:8090`
