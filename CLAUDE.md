# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

V-Link — a volunteering platform (personal project). Promoters publish volunteer events; volunteers browse and subscribe to them. UI copy and code comments are in Portuguese (pt-PT); the product targets Portugal.

Monorepo with two independent apps that only talk over HTTP:
- `backend/` — Spring Boot 3.5.5, Java 17, Maven
- `frontend/` — React 19 + Vite 7 + MUI 7

## Commands

### Backend (`backend/`)
- Requires a JDK 17 installed and `JAVA_HOME` set. This machine didn't have one initially — installed via `winget install --id EclipseAdoptium.Temurin.17.JDK`, landing at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`. Check for a JDK before assuming `./mvnw` will just work.
- Run (dev profile, H2 in-memory DB): requires `JWT_SECRET` env var set first (used in `application.properties` as `jwt.secret=${JWT_SECRET}`).
  - Windows: `mvnw.cmd spring-boot:run`
  - Unix: `./mvnw spring-boot:run`
- Build: `mvnw.cmd clean package` / `./mvnw clean package`
- Run all tests: `mvnw.cmd test` / `./mvnw test`
- Run a single test: `mvnw.cmd test -Dtest=ClassName#methodName`
- H2 console (dev only): `http://localhost:8080/h2` — JDBC URL `jdbc:h2:mem:vlinkdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, user `sa`, no password. Restricted to `ROLE_PROMOTER` (see Security section) — hit it with a promoter's Bearer token, not a bare browser navigation.
- `dev` is the default active profile (`application.yml`). `prod` profile expects Postgres at `localhost:5432/vmp` (user/pass `vmp`).
- **Spring Boot DevTools is on the classpath.** Editing a `.java` file while `spring-boot:run` is already running does *not* hot-reload by itself — you need to trigger a recompile (`./mvnw -q compile`) for the live process's file-watcher to pick it up and restart in-place. For changes that add/alter a non-nullable DB column (new `@ManyToOne`/`@Column` on an existing entity), prefer fully killing and relaunching the process instead — H2 is in-memory, so a same-JVM DevTools restart tries to `ALTER TABLE` around existing seeded rows and can fail; a fresh JVM just recreates the schema from scratch. Find the real PID with `netstat -ano | grep ":8080"` (not `lsof`, which is unreliable in this Git-Bash/Windows setup) and `taskkill //F //PID <pid>`.
- **The H2 in-memory DB is wiped on every backend restart** — any registered users/events are gone. Don't be surprised by sudden 401s after restarting the backend mid-session; re-register.

### Frontend (`frontend/`)
- Install: `npm install`
- Dev server: `npm run dev` (Vite, default port `5173` — the backend's CORS config only allows this exact origin)
- Build: `npm run build`
- Lint: `npm run lint`
- Preview production build: `npm run preview`
- No test runner is configured.

## Architecture

### Backend package layout (`com.vlink.backend`)
- `model` — JPA entities (`User`, `Event`, `Subscription`)
- `repo` — Spring Data repositories (`UserRepository`, `EventRepository`, `SubscriptionRepository`)
- `controller` — REST endpoints (`AuthController`, `EventController`, `SubscriptionController`)
- `dto` — Java records for request bodies (`LoginRequest`, `RegisterRequest`, `RefreshRequest`, `UpdateProfileRequest`)
- `auth` — `JwtUtil` (token creation/parsing) and `JwtAuthFilter` (per-request auth)
- `config` — `SecurityConfig` (filter chain, CORS, password encoder)
- `api` — `ApiExceptionHandler` (global `@RestControllerAdvice`)
- `health` — plain `/api/health` liveness endpoint

Entities use Lombok (`@Getter @Setter @NoArgsConstructor`); request/response bodies favor Java records instead of classes. `spring-boot-starter-validation` is a declared dependency but nothing uses `@Valid`/bean-validation annotations yet — validation that does exist is hand-rolled (e.g. `Event`'s `@PrePersist`/`@PreUpdate` date checks).

There used to be a `UserController` at `/api/users` — it was deleted. It predated the JWT auth work, duplicated `/auth/me`, and turned out to be a real vulnerability: it deserialized a raw `User` straight from the request body with no role restriction, so any authenticated user (any role) could `POST` a new user with an arbitrary `role` and an attacker-chosen (pre-hashed) `password`, bypassing `/auth/register` entirely. If you're tempted to re-add a user-listing/admin endpoint, build it as a proper DTO-based, role-restricted controller — don't resurrect that pattern.

### Security posture
- **Passwords**: BCrypt-hashed via `PasswordEncoder`, both at `/auth/register` and in `BootSeed`'s dev seed data (the seed used to store a plaintext password, which silently broke login — fixed).
- **`User.password` is `@JsonIgnore`** on the entity itself, so no endpoint — present or future — can accidentally serialize a password hash into a JSON response.
- **JWTs are typed.** Every token carries a `type` claim (`"access"` or `"refresh"`) via `JwtUtil.generateToken`/`generateRefreshToken`. `JwtAuthFilter` only accepts `type=access` tokens for normal API calls; `/auth/refresh` only accepts `type=refresh`. Before this, any valid token worked interchangeably as either, which meant a leaked short-lived access token could be used to perpetually mint fresh sessions forever via `/auth/refresh` — don't remove this distinction.
- **Changing your password requires your current password.** `UpdateProfileRequest` has a `currentPassword` field; `AuthController.updateMe` verifies it with `passwordEncoder.matches(...)` before allowing a change (401 if wrong/missing). The frontend's `Profile.jsx` "Alterar password" form has a matching "Password atual" field — keep them in sync if this contract changes.
- **`EventController.create` strips any client-supplied `id`** (`event.setId(null)` before save). Without this, Spring Data's `save()` treats a non-null id as "existing entity" and calls `merge()` instead of `persist()` — meaning a client could `POST /events` with `{"id": <someone else's event>, ...}` and silently overwrite it, fully bypassing the ownership check on `PUT`. This was demonstrated live and fixed; if you add other entities with client-facing create endpoints that deserialize the entity directly (rather than through a DTO), check for the same hole.
- **The H2 console (`/h2`) requires `ROLE_PROMOTER`**, not just "authenticated" — it's a raw SQL console over the whole (dev) DB, and there's no reason a `VOLUNTEER` account should reach it. Still dev-only; disabled in the `prod` profile.
- **Environment quirk, not a code bug**: something on this Windows dev machine (likely AV/endpoint-security software doing loopback traffic inspection) blocks or interactively prompts on HTTP request bodies containing certain accented UTF-8 characters (e.g. `ã`) — reproduced identically via both `curl` and PowerShell's `Invoke-WebRequest`, so it's not a Git-Bash encoding artifact. Plain-ASCII request bodies are unaffected. If a request mysteriously 403s or hangs only when the body contains non-ASCII text, this is why — don't go looking for a bug in the Spring Security config.
- **Known quirk, not a vulnerability**: an *authenticated* request to a path with no controller mapping at all (e.g. the now-deleted `/api/users`) returns `403`, not `404`. Confirmed pre-existing and orthogonal to `anyRequest().authenticated()` — more restrictive than expected, not less, so not worth chasing.
- **Not yet hardened** (known, not fixed, no active exploit found): no rate-limiting/lockout on `/auth/login`; tokens are kept in `localStorage` rather than an httpOnly cookie (no XSS injection point exists today — no `dangerouslySetInnerHTML` anywhere in the frontend — but this is why that matters if one is ever introduced).

### Auth model (stateless JWT)
- `AuthController` issues an access token (15 min, `jwt.expiration`) and a refresh token (7 days, `jwt.refresh-expiration`) on `/auth/register` and `/auth/login`, both carrying the user's `role` and a `type` claim. `/auth/refresh` mints a new pair from a still-valid, correctly-typed refresh token without re-checking the DB.
- `JwtAuthFilter` runs on every request: if the `Authorization: Bearer` token is valid *and* is an access token, it populates `SecurityContextHolder` directly from the token's email + role claims — there is no per-request DB lookup, so a user's identity/role for the duration of a token's life is whatever was baked in at issuance (no server-side revocation).
- Only two roles exist: `VOLUNTEER` and `PROMOTER` (`User.Role`). `SecurityConfig` gates `POST`/`PUT /events/**` to `ROLE_PROMOTER`, `/h2/**` to `ROLE_PROMOTER`; `/auth/me` and `/subscriptions/**` require any authenticated user; `GET /events/**` and `/auth/login|register|refresh` are public.
- Frontend (`frontend/src/api/axiosConfig.js`) stores both tokens in `localStorage`, injects the access token on every request, and on a `401` transparently calls `/auth/refresh`, queuing any other in-flight requests until the refresh completes, then retries — but skips this whole dance for any `/auth/*` request (so a failed login shows its own error instead of triggering a pointless refresh attempt). `authContext.jsx` decodes the JWT payload client-side just to read `role` — it never calls `/auth/me` for that.

### Events
- `Event` (JPA entity) has `Status` (`DRAFT`/`PUBLISHED`/`CLOSED`) and `Type` (`LIMPEZA`/`DOACAO`/`EDUCACAO`/`AMBIENTE`/`SOCIAL`/`OUTRO`) enums, an `organizer` (`@ManyToOne User`, required), and a `@Transient subscriberCount` populated per-request from `SubscriptionRepository`. `@PrePersist`/`@PreUpdate` validation rejects a past `startDate` or an `endDate` before `startDate` (thrown as `IllegalArgumentException`, caught in the controller and returned as `400`).
- `EventController.create` always forces `status = PUBLISHED` regardless of what's sent (no draft workflow in practice) and always sets `organizer` to the authenticated caller, ignoring anything the client sent for it.
- `EventController.update` (`PUT`) checks that the authenticated caller's email matches `event.getOrganizer().getEmail()` and returns `403` otherwise — only the promoter who created an event can edit it.
- `EventRepository.findByFilters` is one dynamic JPQL query filtering by optional `location` (case-insensitive contains), `date` (exact day match on `startDate`), and `type` — used for both the filtered and unfiltered `GET /events` list.

### Subscriptions
- Real persistence: `Subscription` (JPA entity, unique on `(user_id, event_id)`) links `User` and `Event`. `SubscriptionController` (`GET`/`POST`/`DELETE /subscriptions/{eventId}`, all scoped to the authenticated caller's own email) replaced an earlier in-memory `Map` implementation.
- **Capacity is enforced**: `POST /subscriptions/{eventId}` checks `subscriptionRepo.countByEventId(eventId) >= event.getCapacity()` and returns `409` ("Este evento já não tem vagas disponíveis.") once full. `Event.jsx` surfaces this error to the user instead of failing silently.
- `EventController` attaches the real subscriber count to every `Event` response via the `@Transient subscriberCount` field; `Event.jsx` uses `event.subscriberCount` (updated optimistically on subscribe/unsubscribe) instead of a hardcoded value for the "vagas disponíveis" progress bar.
- Gotcha if you touch `SubscriptionRepository` again: a derived `deleteByX` query needs an explicit `@Transactional` on the repository method — Spring Data's delete-by-derivation loads matching entities and calls `remove()`, which throws `TransactionRequiredException` without one. Hit this once already (`deleteByUserEmailAndEventId`).
- `entities.png` (repo root) sketches the originally-planned shape (a `SignUpController` entity with `id`/`event_id`/`user_id`/`status`); the actual `Subscription` entity skips the `status` field since nothing in the app currently needs more than "subscribed or not."

### Known rough edges
- Subscription-related API calls (`getSubscriptions`, `isSubscribed`, `subscribe`, `unsubscribe`) live in `frontend/src/api/user.js`, not `event.js`, even though they operate on events.
- `ApiExceptionHandler` inspects the DB exception's message text (looking for `"email"` vs. `"user_id"`+`"event_id"`) to decide whether a `DataIntegrityViolationException` was a duplicate-email or duplicate-subscription conflict — works for both H2 and Postgres today, but it's string-sniffing the driver's error message, not a structural check. If a new unique constraint is added, extend this rather than assuming it'll classify correctly on its own.
- `EventList.jsx`'s filters are debounced (400ms) with a request-id guard against out-of-order responses — if you add more filter inputs, route their changes through the same `filters` state so they get debounced too, rather than calling `fetchEvents()` directly.

### Frontend structure
- `main.jsx` owns the router (`react-router-dom` v7) and the MUI theme. All authenticated pages render inside a shared `App` layout (persistent `Navbar` + `<Outlet/>`); `Landing`, `Login`, `Register` render standalone.
- `ProtectedRoute` (redirects to `/login` if no token, or `/unauthorized` if `requiredRole` doesn't match) and `AuthRoute` (redirects already-logged-in users away from `/login`/`/register`) are defined inline in `main.jsx`, not as separate files.
- Promoter-only routes: `/new` (`CreateEvent`) and `/events/:id/edit` (`EventEdit`). `Navbar` only shows the "Criar evento" link when `role === "PROMOTER"`. `EventEdit` fetches the event by route id, pre-fills the form (including preserving its current `status` so saving doesn't silently revert it to `DRAFT`), and submits a real `PUT` — mirror `CreateEvent.jsx`'s structure/styling if you touch either.
- `api/axiosConfig.js` hardcodes `baseURL: "http://localhost:8080"` — there's no env-based API URL config.
- Brand theme (defined in `main.jsx`): deep green `#1B4332` (primary), light green `#52B788`, gold `#D4A853` (secondary), cream `#F8F3E6` (background). Headings use Playfair Display, body text uses DM Sans, both loaded from Google Fonts via an inline `<style>` tag rather than a stylesheet import.
- MUI's `CardActionArea` renders as a real `<button>` — never nest another `Button`/interactive element inside one (invalid HTML, browsers silently reparent it out, breaking click behavior). `MySubscriptions.jsx`'s "Cancelar" button was fixed to sit as a sibling next to the `CardActionArea`, not inside it; follow that pattern for any future card-with-action-button layout.
