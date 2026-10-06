# Event Tickets API: Documentation

A REST API for browsing events and booking tickets. Customers register, log in with a JWT, and book up to 4 tickets per booking. Admins manage events.

| Document | What it covers |
|---|---|
| [Requirements](requirements.md) | Scope, roles, numbered functional & non-functional requirements, assumptions |
| [Architecture](architecture.md) | Tech stack, layers, package layout, request flow |
| [Business rules](business-rules.md) | The six booking rules and exactly how each is enforced |
| [API reference](api.md) | Every endpoint with request/response examples and error codes |
| [Database](database.md) | Schema, constraints, migrations, locking strategy |
| [Security](security.md) | Authentication, JWT format, roles, authorization rules |
| [Development guide](development.md) | Local setup, configuration, testing, coding conventions |
| [Testing with PowerShell](testing-with-powershell.md) | Step-by-step manual test of every rule from PowerShell |
| [Testing with curl](testing-with-curl.md) | The same manual test from a macOS / Linux terminal (zsh or bash) |
| [Testing with Postman](testing-with-postman.md) | Endpoints, payloads and a step-by-step manual test plan |

## At a glance

- **Stack:** Java 25, Spring Boot 4.1, Spring Security (OAuth2 resource server, HS256 JWT), Spring Data JPA / Hibernate, PostgreSQL, Flyway, Maven
- **Roles:** `CUSTOMER` (self-registered) and `ADMIN` (created from configuration on startup)
- **Errors:** RFC 9457 `application/problem+json`
- **Base URL (local):** `http://localhost:8090`
