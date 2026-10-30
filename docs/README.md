# Documents and references

## Homework briefs

| # | Brief | Built in |
|---|---|---|
| 01 | [Event Tickets API: business rules to implement](homework/01-backend-api-business-rules.jpg) | [`backend/`](../backend/README.md) |
| 02 | [Event Tickets app: build the app on top of the API rules](homework/02-android-app-rules.jpg) | [`android/`](../android/README.md) |

## Backend documentation

The Event Tickets API: a REST API for browsing events and booking tickets. Customers register, log in with a JWT, and book up to 4 tickets per booking. Admins manage events. Code and setup: [`backend/`](../backend/README.md).

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
| [`api.http`](../backend/api.http) | Runnable requests for every endpoint (IntelliJ / VS Code REST Client) |

At a glance:

- **Stack:** Java 25, Spring Boot 4.1, Spring Security (OAuth2 resource server, HS256 JWT), Spring Data JPA / Hibernate, PostgreSQL, Flyway, Maven
- **Roles:** `CUSTOMER` (self-registered) and `ADMIN` (created from configuration on startup)
- **Errors:** RFC 9457 `application/problem+json`
- **Base URL (local):** `http://localhost:8090`

## Android documentation

- [App README](../android/README.md): how to run it, and where each homework rule is implemented

## Workspace

[`workspace/`](workspace/) holds copies of the root documents (`CLAUDE.md`, `README.md`). Edit the root files, then run `sh docs/workspace/sync.sh` to update the copies.

## External references

- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) (the error format the API returns)
- [Spring Boot reference](https://docs.spring.io/spring-boot/index.html)
- [Jetpack Compose](https://developer.android.com/develop/ui/compose/documentation)
- [Guide to app architecture (Android)](https://developer.android.com/topic/architecture)
- [Retrofit](https://square.github.io/retrofit/) · [OkHttp interceptors](https://square.github.io/okhttp/features/interceptors/)
- [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
