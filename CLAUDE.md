# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

V-Link — a volunteering platform (personal project). Promoters publish volunteer events; volunteers browse and subscribe to them. UI copy and code comments are in Portuguese (pt-PT); the product targets Portugal.

Monorepo with two independent apps that only talk over HTTP:
- `backend/` — Spring Boot 3.5.5, Java 17, Maven
- `frontend/` — React 19 + Vite 7 + MUI 7

## Commands

### Backend (`backend/`)
- Requires JDK 17 + `JAVA_HOME` set (this machine's install: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`).
- Run (dev profile, H2 in-memory DB): needs `JWT_SECRET` env var set — `mvnw.cmd spring-boot:run` (Windows) / `./mvnw spring-boot:run` (Unix).
- Build: `mvnw.cmd clean package`. All tests: `mvnw.cmd test` (no `JWT_SECRET` needed — `src/test/resources/application.properties` overrides it). Single test: `mvnw.cmd test -Dtest=ClassName#methodName`.
- H2 console (dev only): `http://localhost:8080/h2` — `jdbc:h2:mem:vlinkdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, user `sa`, no password. Gated to `ROLE_PROMOTER`.
- `dev` profile is default; `prod` expects Postgres at `localhost:5432/vmp` (user/pass `vmp`).
- **Schema is owned by Flyway, not Hibernate** — both profiles run `ddl-auto: validate`. Migrations in `backend/src/main/resources/db/migration/` (`V1`–`V7`). **Gotcha**: H2 (even in `MODE=PostgreSQL`) rejects multi-column `ALTER TABLE ... ADD COLUMN a, ADD COLUMN b` in one statement — split into separate statements per column.
- Email is off by default everywhere except prod (`app.mail.enabled`) — no SMTP server needed for dev or tests. `EmailService` logs and returns instead of touching `JavaMailSender`.
- Uploaded event images live on local disk under `app.upload.dir` (`./uploads`, gitignored); tests point it at a tmpdir instead.
- **Spring Boot DevTools** needs a recompile (`./mvnw -q compile`) to hot-restart a running `spring-boot:run` process. For schema-affecting changes, kill and relaunch fully instead (H2 is in-memory; a same-JVM restart tries to `ALTER TABLE` around existing rows). PID via `netstat -ano | grep ":8080"`, kill via `taskkill //F //PID <pid>`.
- **H2 is wiped on every backend restart** — re-register test accounts after any restart.

### Frontend (`frontend/`)
- `npm install`, `npm run dev` (port `5173` — the only origin the backend's CORS allows), `npm run build`, `npm run lint`, `npm run test` (Vitest + RTL, jsdom).
- Test coverage: `Login.jsx`, `EventList.jsx`, `OrganizerDashboard.jsx`, `EventSubscribers.jsx`, `EventEdit.jsx`, `MySubscriptions.jsx`, `MyFavorites.jsx`, `VolunteerDashboard.jsx`, `Event.jsx`, `CreateEvent.jsx`, `Navbar.jsx` (notifications + anonymous header), `axiosConfig.js` (401/refresh interceptor), `utils/csv.js`.
- MUI's `required` prop adds `*` to labels — use `getByLabelText("Email", { exact: false })`. For a `<TextField select>`'s options: `userEvent.click` the combobox to open it, then query `within(screen.getByRole("listbox")).getAllByRole("option")`.

## Architecture

### Backend package layout (`com.vlink.backend`)
- `model` — JPA entities (`User`, `Event`, `Subscription`, `RefreshToken`, `Notification`, `Favorite`)
- `repo` — Spring Data repositories
- `controller` — `AuthController`, `EventController`, `SubscriptionController`, `EventSubscriberController`, `NotificationController`, `FavoriteController`
- `dto` — request/response records, bean-validated
- `service` — `FileStorageService`, `EmailService`, `EventReminderScheduler`
- `auth` — `JwtUtil`, `JwtAuthFilter`, `LoginAttemptService`, `RegisterAttemptService`
- `validation` — `@ValidEventDates` / `EventDatesValidator`
- `config` — `SecurityConfig`, `WebConfig`
- `api` — `ApiExceptionHandler`

Entities use Lombok; request/response bodies favor Java records. Every `@RequestBody` in `AuthController`/`EventController` is `@Valid`. `EventController`/`SubscriptionController` return raw entities; `EventSubscriberController`/`NotificationController` map to DTOs (`SubscriberResponse`/`NotificationResponse`) — prefer that pattern for new list/detail endpoints.

**Quirk, not a bug**: `SecurityConfig.java` lives under `.../config/` but declares `package com.vlink.backend.auth;`. Don't "fix" this without checking every import.

There used to be a `UserController` at `/api/users` — deleted. It deserialized a raw `User` from the request body with no role check, letting any authenticated user set an arbitrary `role`/pre-hashed `password`. Don't resurrect that pattern; build any future admin/listing endpoint as DTO-based and role-restricted.

### Security posture
- Passwords BCrypt-hashed; `User.password`/`User.email` are `@JsonIgnore` (email leaked via `Event.organizer` nested serialization on public endpoints — no product use for it).
- **JWTs are typed** (`type: access|refresh`, plus a `jti`). `JwtAuthFilter` only accepts `access` tokens; `/auth/refresh` only accepts `refresh`. Don't let a token work as both.
- Password change requires `currentPassword`, verified via `passwordEncoder.matches`.
- `EventController.create` strips any client-supplied `id` before save — otherwise Spring Data's `save()` would `merge()` an attacker-chosen id, bypassing ownership checks.
- `/h2` console is `ROLE_PROMOTER`-gated, dev-only.
- **`SecurityConfig` matcher order matters** — role-restricted `GET /events/**` sub-paths must be declared before the blanket `permitAll()` line. `GET /uploads/**` is deliberately public.
- Image upload validates declared `Content-Type` only, not file bytes — acceptable here (no execution risk serving own uploads statically), not a pattern to reuse where served files could be interpreted as executable.
- **Env quirk**: this Windows machine's AV/loopback inspection blocks/hangs on HTTP bodies with certain accented UTF-8 chars (e.g. `ã`) via both curl and PowerShell — not a Spring Security bug if a request mysteriously hangs on non-ASCII text.
- **Known quirk**: an authenticated request to an unmapped path (e.g. deleted `/api/users`) returns `403`, not `404`. Pre-existing, not worth chasing.
- **`SecurityConfig` has an explicit `authenticationEntryPoint` returning `401`.** Without one, Spring Security's default fallback (`Http403ForbiddenEntryPoint`) returns `403` for *any* missing/invalid/expired token — which silently broke `axiosConfig.js`'s refresh-on-401 interceptor for natural token expiry (any session over 15 min got stuck permanently 403ing). Found via manual testing, not by the automated suite (no test exercised a genuinely-unauthenticated request through the real filter chain). Wrong-role/ownership `403`s (a separate code path — `AccessDeniedHandler` + controllers' own checks) are untouched. Covered by `SecurityConfigAuthEntryPointTest`.
- `/auth/login` rate-limited (`LoginAttemptService`, 5 failures/15 min per email, in-memory, resets on restart). `/auth/register` rate-limits only the *duplicate-email* failure branch, by IP (`RegisterAttemptService`, 10/15 min) — counting only failures keeps it from tripping on legitimate signups or the test suite; tests that deliberately trip it must call `registerAttemptService.reset(ip)` after (shared singleton across the whole test run).
- Refresh tokens are revocable (persisted `RefreshToken` row per `jti`, rotated on every `/auth/refresh`, revoked on `/auth/logout`); access tokens are not (no per-request DB check — deliberate stateless-JWT tradeoff).
- Tokens live in `localStorage` (no XSS vector today — no `dangerouslySetInnerHTML` anywhere — but relevant if one's ever introduced).
- **Subscribing uses a pessimistic lock** (`EventRepository.findByIdForUpdate`), not just a count check — reproduced 8 concurrent requests oversubscribing a capacity-1 event without it. Reuse this pattern for any other read-check-then-write gated on live counts.
- **`Event` has a `@Version` field** (optimistic locking) so concurrent `PUT`s (e.g. double-submitted "Encerrar") don't both fire closure notifications — loser gets `409` via `ApiExceptionHandler`. Applies to any concurrent edit, not just closing.
- **CSV export neutralizes formula injection** (`utils/csv.js#escapeCsvValue` prefixes `'` before a leading `=+-@`). Any new CSV export must reuse `toCsv`.

### Auth model (JWT + a thin layer of server-side state)
- Access token 15 min, refresh token 7 days, both carry `role`/`type`/`jti`. Every refresh issuance persists a `RefreshToken` row.
- `/auth/refresh` checks the row isn't revoked/expired, then rotates (revoke old, persist new). `/auth/logout` revokes best-effort (never errors, even on a malformed/expired token).
- `JwtAuthFilter` populates `SecurityContextHolder` straight from token claims, no per-request DB lookup — parses the signature exactly once via `JwtUtil.parseValidClaims` (don't reintroduce separate re-verifying calls).
- Two roles: `VOLUNTEER`, `PROMOTER`. `SecurityConfig`: `POST`/`PUT`/`DELETE /events/**`, `GET /events/mine`, `GET /events/*/subscribers`, `/h2/**` → `ROLE_PROMOTER`; `/auth/me`, `/subscriptions/**`, `/notifications/**`, `/favorites/**` → any authenticated user; `GET /events`, `GET /events/{id}`, `GET /uploads/**`, `/auth/login|register|refresh|logout` → public. **`GET /events/{id}` being public is what makes the public event detail page (`Event.jsx`, unauthenticated) work — nothing to change server-side for that page.**
- Frontend (`axiosConfig.js`) stores both tokens in `localStorage`, injects the access token, and on `401` transparently refreshes + queues/retries in-flight requests (skipped for `/auth/*` requests). `authContext.jsx` decodes the JWT client-side for `role` (never calls `/auth/me`); `logout()` clears `localStorage` synchronously, then calls `/auth/logout` best-effort.

### Events
- `Event` has `Status` (`DRAFT`/`PUBLISHED`/`CLOSED`), `Type` enum, `organizer`, `@Transient subscriberCount`, `@Version`. Validation is field-level annotations + a class-level `@ValidEventDates` (endDate not before startDate).
- **`status`/`type` have no Java field default** — a default would apply before Jackson calls setters, making "omitted" indistinguishable from "explicitly default," which silently un-published/re-typed events on `PUT`. `update()` rejects null `status`/`type` with `400`; `create()` still treats omitted `status` as "save as draft" (its own explicit line, unaffected).
- **`startDate`/`endDate` are `LocalDateTime` (no timezone), treated as server-local wall-clock time everywhere.** Don't submit them via `.toISOString()` from the frontend — that converts to UTC, and during Portuguese summer time (WEST, UTC+1) shifted every date an hour early, making near-future events read as already past. Send the `datetime-local` value as-is (`${v}:00`).
- Past-start-date rejection is **create-only** (`EventController.create`), not in `EventDatesValidator` — a shared validator would also reject editing/closing an event that's already started.
- **Draft/publish**: `create()` — omitted/non-`PUBLISHED` status → `DRAFT`; `CLOSED` rejected outright. `update()` requires a non-null status (any of the three).
- Only the owning promoter can `PUT`/`DELETE` an event (`403` otherwise).
- **Status transitions on `PUT` follow a state machine**, not "any of three enum values." Forbidden: `CLOSED → {DRAFT, PUBLISHED}` (closed is permanent) and `PUBLISHED → DRAFT` **when the event has subscribers** (zero-subscriber un-publish is harmless and allowed — an unconditional block was a real regression caught in manual testing). Allowed and untouched: `DRAFT → CLOSED`, same-status resaves (`CLOSED → CLOSED` is the idempotent no-op `concurrentCloseRequestsNeverNotifyTwice` relies on). Without this, a `PUBLISHED`-with-subscribers event could be force-deleted via `PUT status:DRAFT` then `DELETE` (which allows `DRAFT` regardless of dates/subscribers), bypassing every close/cancel timing rule below. `EventEdit.jsx`'s status `<select>` mirrors this via `allowedStatusOptionsFrom(originalStatus, originalSubscriberCount)`.
- `PUT` rejects reducing `capacity` below the current subscriber count (would render "vagas disponíveis" negative).
- **Rescheduling** a `PUBLISHED` event's dates while it has subscribers notifies them (`notifySubscribersOfReschedule`) — only when it *stays* `PUBLISHED` and dates actually changed (exact `LocalDateTime` equality, so tests must reuse persisted date strings rather than recomputing "now + N hours" a second time).
- `EventRepository.findByFilters` (backs `GET /events`) is hardcoded to `status = 'PUBLISHED' AND endDate >= :now` — **`PUBLISHED` does not mean "still happening"**; nothing auto-closes an event once its `endDate` passes, so the date filter matters independently of status. `GET /events/{id}` and `GET /events/mine` are deliberately unfiltered (direct links, organizer dashboard). The same `endDate < now` check gates `subscribe`/`unsubscribe` (see Subscriptions) — deliberately not the close/cancel guards, which key off `startDate` instead. `Event.jsx` mirrors this client-side (`canModifySubscription = status === "PUBLISHED" && !isPast`), showing a disabled "Inscrito ✓" badge or an info alert instead of a button the server would reject.
- **`findByFilters` also takes an optional `keyword`** (Milestone 3), OR-matched against `LOWER(title)`/`LOWER(description)` — `description` is nullable, and `LOWER(NULL) LIKE ...` is SQL-null-safe (evaluates to not-matched, no `COALESCE` needed). `EventList.jsx`'s "Pesquisar" field feeds this, wired through the same debounced `filters` state as `location`/`date`/`type`.
- **"Encerrar" (close, `PUT status:CLOSED`) and "Cancelar" (cancel, `DELETE`) are two different actions, mutually exclusive by timing:**
  - Close requires the event to have *started* (`400` otherwise, checking the persisted `startDate`, not whatever else is bundled into the same request). Notifies subscribers; `Subscription`/`checkedIn` rows are untouched — closed is a permanent historical record.
  - Cancel is for a `DRAFT` or a `PUBLISHED` event that *hasn't* started — hard-deletes the event, its subscriptions, its favorites, and its image, notifying subscribers first with a self-contained message (`Notification.event` nulled, survives the deletion) and, if `app.mail.enabled`, an email. Rejected for an already-started `PUBLISHED` event (close instead) or any `CLOSED` event (never deletable — would destroy `checkedIn` history). **`favoriteRepo.deleteByEventId` in `delete()` is required, not optional** — a `Favorite` row has an FK to `events`, so deleting an event someone favorited throws `DataIntegrityViolationException` without it (a real bug, fixed alongside adding Favorites).
  - Because of this split, `MySubscriptions.jsx` needs no changes for the cancel path (row just disappears with the DB row). The close path did need one — see Subscriptions.
- Image upload: `POST /events/{id}/image` stores to `/uploads/events/{id}/{uuid}.jpg` via `FileStorageService`; re-uploading deletes the previous file (`deletePreviousImage`, only after the new one saves successfully, safe with null/external legacy URLs).

### Organizer tools (Milestone 2)
- **Organizer Dashboard** (`/dashboard`, `ROLE_PROMOTER`): `GET /events/mine`, all statuses. Exactly one destructive action per row, by status *and* `hasStarted`: `DRAFT`→Eliminar, `PUBLISHED` not started→Cancelar, `PUBLISHED` started→Encerrar, `CLOSED`→none. Gate any new status-dependent action the same way (status + timing), not status alone.
- **"Editar" is gated by `hasEnded` (`endDate < now`), not by status.** A `CLOSED` event closed *before* its `endDate` still shows Editar (there's a real reason to fix a typo before it actually happens); once `endDate` passes, editing is hidden regardless of status — nothing left to change on something that already happened. `Edit` and `Ver inscritos` render as `<a>` (`component={RouterLink}`), so tests must query `getByRole("link", ...)`, not `"button"`. `Navbar.jsx` no longer has its own "Criar evento" link — it's already on the dashboard.
- **Event Subscribers view** (`/events/:id/subscribers`): `GET /events/{id}/subscribers` → `SubscriberResponse` DTOs. CSV export is fully client-side, no backend endpoint.
- **Attendance/check-in**: `Subscription.checkedIn`/`checkedInAt` (`V4`). `PUT /events/{eventId}/subscribers/{userId}/attendance`, `404` if not subscribed. Foundation for Milestone 3 hours tracking — don't drop `checkedInAt`.

### Notifications (in-app only)
- `Notification` (`V3`): `recipient_id`, nullable `event_id`, `message`, `is_read`, `created_at`. Created only by closure/cancellation/reschedule transitions — no generic "send notification" endpoint.
- `NotificationController`: `GET /notifications`, `GET /notifications/unread-count`, `PUT /notifications/{id}/read` (404 if not yours), `PUT /notifications/read-all`.
- `Navbar.jsx` polls unread-count every 30s; visible to every authenticated role (volunteers are the recipients).

### Subscriptions
- `Subscription` (unique on `user_id, event_id`) via `SubscriptionController` (`GET`/`POST`/`DELETE /subscriptions/{eventId}`).
- `POST` (subscribe) rejects if the event isn't `PUBLISHED`, or if `endDate < now` (already-ended-but-never-closed). Capacity enforced race-safe via the pessimistic lock (see Security posture); `409` once full.
- `DELETE` (unsubscribe) rejects if the event is `CLOSED` **or** already ended (`endDate < now`) — both cases protect `checkedIn` history, since check-in itself has no date guard. `MySubscriptions.jsx`'s `getStatus()` checks `event.status === "CLOSED"` first (label `"encerrado"`, button hidden) before falling back to date math (`"passado"` also hides the button) — gate by real status/dates, never a date-only guess, anywhere in the volunteer-facing UI.
- `EventController` attaches live `subscriberCount` to every `Event` response.
- Derived `deleteByX` repo methods need an explicit `@Transactional` (Spring Data's delete-by-derivation calls `remove()`, which needs a transaction).
- **`GET /subscriptions/summary`** (Milestone 3, backs the Volunteer Dashboard) splits the caller's subscriptions into `upcomingEvents` (`endDate >= now`) and `pastEvents` (`{event, checkedIn}` pairs, `endDate < now` — includes events the volunteer never got checked into, not just attended ones), plus `totalHours`. **Hours only sum `checkedIn=true` past subscriptions** — registering without the promoter confirming attendance contributes zero. Computed in Java (`Duration.between`), not JPQL/SQL, to dodge H2/Postgres date-diff dialect differences. This is a literal-path sibling of `GET /subscriptions/{eventId}`, same precedent as `/events/mine` next to `/events/{id}`.

### Favorites (Milestone 3)
- `Favorite` (`V6`, unique on `user_id, event_id`) via `FavoriteController` (`GET`/`GET /{eventId}`/`POST`/`DELETE /favorites/{eventId}`) — deliberately modeled as a **separate entity from `Subscription`**, not a flag on it: a favorite is a no-commitment bookmark, with **no status/date/capacity restriction** (you can favorite a `DRAFT`, a `CLOSED`, or an already-ended event) — conflating it with `Subscription` would leak the capacity/state-machine guards into a feature that's supposed to have none.
- `POST`/`DELETE` are idempotent (mirrors `subscribe`/`unsubscribe`'s forgiving behavior) — no pessimistic lock (nothing scarce to protect).
- `MyFavorites.jsx` (`/favorites`, any authenticated role) is `MySubscriptions.jsx`'s styling twin — a bookmark toggle with no way to browse back to it isn't usable end-to-end.
- **Icon convention**: `Event.jsx`'s heart toggle uses `FavoriteBorder`/`Favorite` — **not** the `Bookmark*` icon family, which already means subscribe/unsubscribe on that same page (`BookmarkAdd`/`BookmarkAdded` in `Event.jsx`, `BookmarkRemove` in `MySubscriptions.jsx`). Reusing `Bookmark*` for favorites would visually collide with the subscribe button right next to it.

### Email notifications and reminders (Milestone 3)
- `EmailService` (best-effort, mirrors `FileStorageService`'s try/swallow idiom) sends signup confirmations (`SubscriptionController.subscribe`, only on a genuinely new subscription — not the idempotent-already-subscribed path), closure emails, and cancellation emails — the last two fire on **both** "Encerrar" and "Cancelar" (see Events), alongside the equivalent in-app `Notification`, not instead of it.
- **Gated behind `app.mail.enabled`** (`false` by default, `true` in `application-prod.yml`) — when disabled, `EmailService` logs and returns without touching any `JavaMailSender` bean, so `spring-boot-starter-mail`'s autoconfiguration (which only activates when `spring.mail.host` is set) never needs to fire in dev/test. A `MailException` never propagates past `EmailService`.
- **Reminders**: `EventReminderScheduler` (`@Scheduled`, cron `app.mail.reminder-cron`, default every 15 min) emails subscribers of `PUBLISHED` events starting within `app.mail.reminder-window-hours` (default 24h) who haven't been reminded yet. Dedup via `Subscription.reminderSentAt` (`V7`), **not** a separate tracking table — the subscription row is already the natural 1:1 unit.
  - Cancel/unsubscribe need no extra handling: the `Subscription` row is deleted, so there's nothing left to remind about.
  - **Reschedule resets `reminderSentAt` to `null`** (`EventController.update`'s existing `rescheduled` branch calls `subscriptionRepo.clearReminderSentAt`) — the row survives a reschedule (only the `Event` changes), so without this, someone already reminded for the old time would never be reminded for the new one.

### Validation and error shape
- `ApiExceptionHandler.handleValidation` → `400` `{"error": "Dados inválidos.", "errors": {field: message}}`.
- Cross-field `Event` date-order check is `@ValidEventDates`/`EventDatesValidator` (class-level, reports via `addPropertyNode` so it surfaces as a normal field error). Past-start-date is deliberately *not* here (create-only, see Events).
- Frontend convention: catch blocks check `err.response?.data?.errors` first, then `?.error`, then a generic message (`register`'s duplicate-email `400` is a bare string, hence a `typeof data === "string"` branch in `Login.jsx`/`Register.jsx`).

### Testing
- Backend controller tests are full `@SpringBootTest @AutoConfigureMockMvc`, real H2, driven through `MockMvc` HTTP calls. Context (and DB) is shared across a class's tests — every test uses a fresh `UUID.randomUUID()` email.
- `EventControllerTest.createAlreadyStartedEvent(token, status)` creates with future dates then `PUT`s them into the past (update, unlike create, doesn't reject past dates) — use this to simulate "already started."
- Multipart tests: chain `.file(...)` onto `multipart(url)`, don't pass the file as a vararg to `multipart(url, ...)`.
- Concurrency tests (`ExecutorService` + `CountDownLatch`) assert the durable invariant (final row/notification count), never the `200`/`409` split — that's timing-dependent and flaky to pin down exactly.
- **Spring caches the test `ApplicationContext` (and its H2 DB) across test *classes* that share the exact same configuration**, not just across methods within one class — e.g. `EmailNotificationTest` and `EventReminderSchedulerTest` both declare `@SpringBootTest @AutoConfigureMockMvc` + `@MockitoBean EmailService` and end up sharing one context/DB, even though they're different files. A scheduler test that scans a whole table (`EventReminderScheduler` has no per-user scope) can pick up leftover rows from an unrelated class's tests. Scope assertions to the specific entity you created (e.g. `argThat` matching on `event.getId()`), never to a raw global invocation count, for anything that queries broadly like this.
- Frontend tests mock `api/*.js` modules with `vi.mock`; pages reading a route param render inside `MemoryRouter` + `Routes` with `initialEntries`.
- `Event.jsx` now calls `useAuth()` — its test file mocks `../context/authContext` (`useAuth: vi.fn()`) with a top-level `beforeEach` defaulting to `{ token: "fake-token" }`; any new test needing anonymous behavior overrides with `{ token: null }`. `axiosConfig.test.js` reaches into `api.interceptors.response.handlers[0].rejected` to unit-test the interceptor directly — the retried request has no real backend to hit, so tests that get that far `.catch(() => {})` the expected network failure rather than asserting on it.

### Known rough edges
- Subscription API calls live in `frontend/src/api/user.js`, not `event.js`, despite operating on events.
- `ApiExceptionHandler` classifies `DataIntegrityViolationException` by string-sniffing the DB error message (`"email"` vs `"favorite"` vs `"user_id"`+`"event_id"`). **Order matters**: `favorites` and `subscriptions` share the same `user_id`/`event_id` column names, so the `favorite` check must come before the generic `user_id`+`event_id` one, or a favorites-uniqueness conflict gets misclassified as `SUBSCRIPTION_CONFLICT`. Extend this, don't assume, if a new unique constraint is added — put the more specific check first.
- `EventList.jsx`'s filters are debounced (400ms) with a request-id guard — route any new filter input through the same `filters` state.

### Frontend structure
- `main.jsx` owns the router + MUI theme; authenticated pages render inside a shared `App` layout (`Navbar` + `Outlet`).
- `ProtectedRoute`/`AuthRoute` are defined inline in `main.jsx`. Promoter-only routes: `/new`, `/events/:id/edit`, `/dashboard`, `/events/:id/subscribers`. `/my-dashboard` (Volunteer Dashboard) and `/favorites` are `ProtectedRoute` with no `requiredRole` — either role can subscribe/favorite.
- **`events/:id` is deliberately NOT wrapped in `ProtectedRoute`** (Milestone 3, public event detail page) — `Event.jsx` handles the anonymous case itself (gates `isSubscribed`/`isFavorited` on `useAuth().token` existing, shows a sign-in CTA instead of the subscribe/favorite actions). Every other `events/:id/...` sub-route (`edit`, `subscribers`) stays protected — only the bare detail path is public.
- `components/Navbar.jsx` renders a minimal anonymous variant (logo + "Entrar") instead of `null` when there's no token — needed so a visitor following a shared event link sees *something*, not a blank header.
- `api/axiosConfig.js` hardcodes `baseURL: "http://localhost:8080"` (no env-based config). **The 401-refresh interceptor checks `localStorage.getItem("refreshToken")` before attempting a refresh** — without that check, an anonymous request to any authenticated endpoint (e.g. `isSubscribed` called from the now-public `Event.jsx`) triggered a doomed refresh attempt ending in a hard `window.location.href = "/login"`, bypassing React Router and breaking the public page regardless of the route itself being open. A real bug, found and fixed as part of Milestone 3.
- `utils/image.js#resolveImageUrl` prefixes `API_BASE_URL` onto relative `/uploads/...` paths, passes absolute (legacy) URLs through — always use it, never render `event.imageUrl` directly.
- `utils/csv.js` (`toCsv`/`downloadCsv`) is kept separate from any component so it's unit-testable without stubbing jsdom's blob/anchor machinery.
- Brand theme (`main.jsx`): deep green `#1B4332`, light green `#52B788`, gold `#D4A853`, cream `#F8F3E6`; Playfair Display headings, DM Sans body.
- MUI's `CardActionArea` renders a real `<button>` — never nest another interactive element inside one; put action buttons as a sibling instead.
- `Event.jsx`/`EventSubscribers.jsx` guard per-id fetch effects against stale responses with a `cancelled` flag (same idea as `EventList.jsx`'s `latestRequestId` ref).
- `CreateEvent.jsx` revokes its image preview blob URL on change/unmount; `EventEdit.jsx` doesn't need this (its preview is an already-uploaded server path).
