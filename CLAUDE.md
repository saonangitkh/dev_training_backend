# Event Tickets

- `backend/`: Spring Boot 4.1 API (Java 25, PostgreSQL, JWT). Run with `cd backend && docker compose up -d --build` (API on :8090).
- `android/`: Kotlin + Jetpack Compose app (MVVM). Build and test with `cd android && ./gradlew assembleDebug testDebugUnitTest`.
- `docs/homework/`: the homework briefs. `android/README.md` maps each app rule to the code that implements it.

## Android UI rules

**Always use the shared UI components in `android/app/src/main/java/com/devtraining/ticketsapp/ui/components/`.**

- Screens (`ui/login`, `ui/events`, `ui/eventdetail`, `ui/bookings`, ...) build their UI from shared components:
  `AppTopBar`, `AppTextField` / `PasswordField`, `DateTimeField`, `LoadingButton`, `LinkButton`, `DangerTextButton`, `AddFab`, `QuantityStepper`,
  `TicketCard`, `EventCard`, `EventDetailCard`, `BookingCard`, `StatusBadge` / `EventStatusBadge` / `BookingStatusBadge`,
  `ResultMessage`, `ConfirmDialog`, `LoadingView`, `MessageView`, `ErrorView`.
- Don't call Material widgets (`TopAppBar`, `OutlinedTextField`, `Button`, `TextButton`, `Card`, `AlertDialog`, ...)
  directly from a screen. If no shared component fits, add one to `ui/components/` first, then use it.
- Every new or changed component gets a section (or an example) in `ui/gallery/ComponentGalleryScreen.kt`,
  using `ui/gallery/SampleData.kt`, so it can be checked without the backend.
- Colors come from `MaterialTheme.colorScheme` roles (see `ui/theme/Color.kt` for the mapping); never hard-code colors
  in components or screens.

## Android architecture rules

- One `UiState` (`ui/UiState.kt`) per request: Idle / Loading / Success / Error.
- The server decides: never compute seats or totals on the phone; reload from the API after every booking or cancel.
- Validate early in the ViewModel (1 to 4 tickets) so invalid requests are never sent.
- API errors become friendly messages in `data/remote/ApiCall.kt`; screens only show `UiState.Error.message`.

## Workspace copies of root documents

- `docs/workspace/` holds copies of the documents at the repo root (`CLAUDE.md`, `README.md`, and any other root `*.md`).
- The root files are the originals: edit those, never the copies in `docs/workspace/`.
- After changing, adding or deleting a root `*.md`, run `sh docs/workspace/sync.sh`.
  It copies only the files that changed and removes copies whose root file is gone.
