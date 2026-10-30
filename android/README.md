# Event Tickets — Android app

Kotlin · Jetpack Compose · MVVM · Retrofit + OkHttp · kotlinx.serialization · EncryptedSharedPreferences

The phone client for the Event Tickets API in [`../backend`](../backend/README.md). **The server decides**; the app validates early, shows state, and turns errors into friendly messages. Homework brief: [`02-android-app-rules.jpg`](../docs/homework/02-android-app-rules.jpg).

## Run it

1. Start the API: `cd ../backend && docker compose up -d --build` (API on `http://localhost:8090`).
2. Open this `android/` folder in Android Studio and let Gradle sync.
3. Run the `app` configuration on an emulator. The app calls `http://10.0.2.2:8090/`, which is your computer as seen from the emulator. On a real device, change `API_BASE_URL` in `app/build.gradle.kts` to your computer's LAN IP and add it to `res/xml/network_security_config.xml`.
4. Log in with `admin@tickets.local` / `Admin@12345`, or tap **New here? Create an account** to register a customer.

## What you can do

- **Customer:** register, log in and out, browse events, book 1–4 tickets, see and cancel your bookings.
- **Admin:** log in and out, browse events, **New event** (button on the event list), **Edit** and **Cancel event** (on the event detail screen), and **All bookings** (every customer's bookings, with their name and email). Admins don't book, so the ticket stepper and Book button are hidden for them. These controls are shown only to admins; the server still checks the role on every request.
- **Once an event has started** its card shows a grey **STARTED** badge and "Not on sale", and Book, Edit, Cancel event and a booking's Cancel are hidden or disabled. **Cancelling an event** cancels all its bookings. The server enforces both (409); the app only hides what would be refused.

## Project structure

```
app/src/main/java/com/devtraining/ticketsapp
├── TicketsApp.kt          Application + AppContainer (manual DI: OkHttp, Retrofit, repositories)
├── MainActivity.kt        Shows LoginScreen / RegisterScreen or AppNavHost depending on TokenStore.loggedIn
├── data/
│   ├── local/TokenStore   Encrypted token and role storage (isAdmin shows the admin controls)
│   ├── remote/            TicketsApi (Retrofit), DTOs, AuthInterceptor, apiCall() error mapping
│   └── repository/        AuthRepository, EventRepository, BookingRepository
└── ui/
    ├── UiState.kt         Idle / Loading / Success / Error
    ├── login/  register/  events/  eventdetail/  eventform/  bookings/   Screen + ViewModel per feature
    ├── components/        Shared UI used by every screen: AppTopBar, AppTextField, PasswordField, DateTimeField,
    │                      LoadingButton, LinkButton, DangerTextButton, AddFab, QuantityStepper, StatusBadge, TicketCard, EventCard,
    │                      EventDetailCard, BookingCard, ResultMessage, ConfirmDialog, Loading/Empty/Error views
    ├── gallery/           ComponentGalleryScreen + SampleData (works without the backend)
    └── navigation/        AppNavHost
```

## UI components gallery

Every reusable component is shown with sample data on one screen, so you can design the UI before the API is running:

- **In the app:** tap **View UI components** on the login screen.
- **In Android Studio:** open `ui/gallery/ComponentGalleryScreen.kt` and use the Split/Design view (`ComponentGalleryPreview`).

Screens only use these shared components, never Material widgets directly (see `../CLAUDE.md`). Add a new component to `ui/components/`, then add a section for it in `ComponentGalleryScreen`.

## Where each homework rule lives

| # | Rule | Where |
|---|------|-------|
| 01 | Loading, error, success: one `UiState` per request; Book shows a spinner and is disabled while it runs; the total comes from the response | `ui/UiState.kt`, `EventDetailViewModel.onBookClick`, `EventDetailScreen`, `EventDetailViewModelTest` |
| 02 | Seats left always come fresh from the API (`availableSeats`); reload after every booking or cancel, never subtract on the phone | `EventDetailViewModel.onBookClick` → `loadEvent()`; screens reload on resume (`LifecycleResumeEffect`) |
| 03 | 1–4 tickets are checked in the ViewModel: the stepper stops at 4, and 0 or 5+ never reaches the API | `EventDetailViewModel.onQuantityChange` / `onBookClick`, `EventDetailViewModelTest` |
| 04 | My Bookings has loading, empty and error states; Cancel asks for confirmation, then reloads the list (and the seats when the event screens are shown again) | `MyBookingsViewModel`, `MyBookingsScreen`, `MyBookingsViewModelTest` |
| 05 | On 409, show the server's message, reload the event and stay on the screen; Book and the stepper are disabled for `CANCELLED` or sold-out events | `data/remote/ApiCall.kt`, `EventDetailUiState.canBook`, `EventDetailViewModelTest` |
| 06 | Stay logged in: the token is saved encrypted; an interceptor adds it to every request; a 401 or logging out clears it and shows login | `TokenStore`, `AuthInterceptor`, `MainActivity` |

## Tests

```sh
./gradlew test
```

ViewModel tests run against `FakeTicketsApi` (in `src/test/.../testing/`), so no server or emulator is needed:

- `EventDetailViewModelTest`: stepper stays within 1–4, Book is blocked until loaded and for cancelled or started events, the total comes from the response, seats reload after booking, and a 409 shows the server's message.
- `MyBookingsViewModelTest`: loading, empty and error states, Cancel asks first, and a confirmed cancel reloads the list.
- `EventFormViewModelTest`: a valid form is sent trimmed; a missing or past date, 3-decimal price or 0 seats is never sent; editing loads the event and saves with PUT; a 409 shows the server's message.
- `RegistrationValidationTest`: name, email and password (8+ characters) are checked before sending.
- `EventDetailViewModelTest` also covers the admin's Cancel event: it asks first, then reloads the event; Edit and Cancel event are offered only while the event is scheduled and hasn't started.
