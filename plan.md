# V-Link Roadmap

Development plan for what comes next, past the current MVP. Ordered so each milestone builds on a stable foundation before adding more surface area: harden what exists → give promoters real tools → give volunteers a richer experience → ship it.

## Milestone 1 — Harden the MVP

Foundation work before adding more surface area on top of it.

- [x] Add bean-validation annotations (`@Valid`, `@NotBlank`, `@Size`) on DTOs and `Event`, replacing the ad-hoc `@PrePersist` date checks with proper validation errors
- [x] Add a real test suite: backend (`@SpringBootTest`/`MockMvc` for controllers, unit tests for `JwtUtil`/repositories), frontend (Vitest + React Testing Library for at least the auth flow and event list)
- [x] Add a `POST /auth/logout` / token-revocation mechanism (tokens currently can't be invalidated early)
- [x] Move from `ddl-auto: update` to Flyway migrations (works for both H2 dev and Postgres prod), so schema changes are explicit and reviewable
- [x] Add rate-limiting/lockout on `/auth/login` (no brute-force protection today)
- [x] Implement the draft/publish workflow properly — event creation currently always forces `PUBLISHED`; add a way for promoters to save a draft and publish later
- [x] **Milestone review**: confirm every task above is checked off; re-read the milestone against the current code and note anything that needs to change before moving to Milestone 2

  **Review notes (2026-08-06):** All six tasks shipped and verified (backend: 16 MockMvc/unit tests; frontend: 4 Vitest/RTL tests; manual curl walkthrough of the full draft→publish→subscribe→logout flow against a fresh H2 instance). Implementation choices, scoped down from the broadest possible version of each task:
  - **Logout** revokes only the refresh token (new `RefreshToken`/`jti` table); the already-issued ≤15-min access token keeps working until natural expiry — no per-request revocation check was added, so `JwtAuthFilter` stays stateless. Full immediate revocation would need a denylist checked on every request.
  - **Rate limiting** is a hand-rolled in-memory `ConcurrentHashMap` in `LoginAttemptService` (5 failures/15 min per email), not a library — fine for the single-instance deployment target, but won't survive a restart or scale past one instance without moving to something shared (Redis) once Milestone 4's deployment target is decided.
  - **Draft/publish** has no dedicated UI for a promoter to find their own drafts again — `CreateEvent.jsx` can save as draft or publish directly, and `EventEdit.jsx` can change status, but there's no listing of "my events" yet. That's explicitly Milestone 2's Organizer Dashboard; nothing here needs to change, just noting the dependency.
  - **Flyway** baseline (`V1__init.sql`) was hand-written to match Hibernate's prior auto-DDL rather than generated, since ddl-auto had to switch to `validate` in the same step — verified by a fresh boot against wiped H2 with no `SchemaManagementException`.
  - Nothing discovered here changes Milestone 2's scope as written.

## Milestone 2 — Organizer tools

Promoters currently have no way to manage what they've created beyond the create/edit forms.

- [x] **New page: Organizer Dashboard** — list of events created by the logged-in promoter, with subscriber counts and quick edit/close links
- [x] **New page: Event Subscribers view** — per-event list of volunteers who signed up (name/email), with CSV export
- [x] Feature: event image upload (replace the current URL-only field) — needs a storage backend (local disk to start, or S3-compatible later)
- [x] Feature: close/cancel an event, notifying already-subscribed volunteers (in-app notifications — no email infrastructure exists until Milestone 3)
- [x] Feature: attendance/check-in tracking — mark which subscribers actually showed up (foundation for volunteer-hours tracking in Milestone 3)
- [x] Seed a subscribed (and checked-in) volunteer in `BootSeed.java`'s dev data, so the dashboard/subscribers/attendance UI have non-empty data to click through on a fresh H2 instance
- [x] **Milestone review**: confirm every task above is checked off; re-read the milestone against the current code and note anything that needs to change before moving to Milestone 3

  **Review notes (2026-08-07):** All five feature tasks plus the seed-data addition shipped and verified (backend: 20 new/extended MockMvc tests across `EventControllerTest`, `EventSubscriberControllerTest`, `NotificationControllerTest`, `EventImageUploadTest`; frontend: 6 new Vitest/RTL tests; a full Playwright walkthrough against the real dev servers covering dashboard → subscribers → attendance → CSV export → image upload → close → volunteer notification). Implementation choices, scoped down from the broadest possible version of each task:
  - **Notifications** are a new `Notification`/`is_read` table populated only on a PUBLISHED→CLOSED transition, one row per subscriber — no push/email, just a polled (30s) unread-count badge and dropdown in the Navbar. Still no per-request access-token revocation or any other Milestone-3-adjacent infra was pulled forward.
  - **Attendance** added `checked_in`/`checked_in_at` to `subscriptions` via `V4__subscription_checkin.sql`. H2 (in `MODE=PostgreSQL`) rejected a single multi-column `ALTER TABLE ... ADD COLUMN a, ADD COLUMN b` statement that Postgres itself would accept — split into two separate `ALTER TABLE` statements for cross-DB safety; worth remembering for any future multi-column migration.
  - **Image upload** is local-disk only (`app.upload.dir`, default `./uploads`), served publicly via a `/uploads/**` static resource mapping — no auth on the images themselves since the events they belong to are already public data. `EventEdit.jsx` uploads immediately on file selection; `CreateEvent.jsx` defers the upload until after the event is created (needs the id first) as a deliberate two-request sequencing tradeoff.
  - **CSV export** is fully client-side (`utils/csv.js`, `Blob` + temporary `<a download>`) against the already-fetched subscriber list — no dedicated backend endpoint, since the app's scale doesn't justify one.
  - **Organizer Dashboard** is a new `GET /events/mine`, scoped server-side to the authenticated promoter's own email, returning all statuses (including drafts) — distinct from the public `GET /events` list, which stays hardcoded to `PUBLISHED` only.
  - Confirmed the pre-existing logout-redirects-to-`/login`-instead-of-`/` bug (noted in `CLAUDE.md`, predates this milestone) is still present and unrelated to any Milestone 2 change — still not fixed, pending explicit direction.
  - Nothing discovered here changes Milestone 3's scope as written.

  **Post-milestone fixes (2026-08-07):** A run of user bug reports plus a deliberate use-case sweep reworked the event lifecycle and closed several real gaps, each shipped with tests confirmed to fail without the fix and pass with it:
  - **Close vs. cancel split.** "Encerrar" was covering two different situations (ending something running vs. something that was never going to happen), which is why the volunteer-facing status chip couldn't reliably tell them apart. Split into "Encerrar" (`PUT status:CLOSED`, only once started, notifies, preserves history permanently) and "Cancelar" (`DELETE /events/{id}`, only for a `DRAFT` or not-yet-started `PUBLISHED` event, hard-deletes + notifies with a self-contained message). `CLOSED` is never deletable.
  - **Timezone bug.** Frontend submitted dates via `.toISOString()` (UTC) while the backend's `LocalDateTime` fields are server-local wall-clock time — shifted every date an hour early during Portuguese summer time, rejecting near-future events as "past." Fixed by submitting the `datetime-local` value as-is.
  - **"Passado" (ended but never closed) wasn't handled anywhere.** A `PUBLISHED` event past its `endDate` stayed fully live: publicly listed, subscribable, and its subscriptions (with `checkedIn` history) still cancellable. Added `endDate < now` checks to the public list query, `subscribe`, and `unsubscribe`; `Event.jsx`/`MySubscriptions.jsx` now reflect real status/dates instead of guessing from dates alone.
  - **Status state machine.** `update()` had no restriction on which status transitions were legal, so a `PUBLISHED`-with-subscribers event could be force-deleted via `PUT status:DRAFT` → `DELETE` (bypassing every timing rule above), and a `CLOSED` event could be reopened the same way. Fixed by forbidding `CLOSED → {DRAFT, PUBLISHED}` and `PUBLISHED → DRAFT` when the event has subscribers (zero-subscriber un-publish stays legal — an earlier unconditional block was a real regression caught in manual testing). `EventEdit.jsx`'s status dropdown mirrors the rule.
  - **Auth entry point bug.** `SecurityConfig` never set an explicit `authenticationEntryPoint`, so Spring Security's default (`Http403ForbiddenEntryPoint`) returned `403` for any missing/expired/invalid token instead of `401` — silently disabling `axiosConfig.js`'s refresh-on-401 flow for natural token expiry. Found via manual testing (a tab left open long enough for a token to expire); fixed with an explicit `401` entry point. Wrong-role `403`s are a separate, unaffected code path.
  - Smaller fixes from the same sweep: capacity can't be reduced below the current subscriber count; rescheduling a `PUBLISHED` event's dates now notifies subscribers; re-uploading an event image deletes the previous file instead of orphaning it.

  Nothing here changes Milestone 3's scope.

## Milestone 3 — Volunteer experience

Volunteers currently only have a flat event list and "my subscriptions" — no personalization or follow-through.

- [ ] **New page: Volunteer Dashboard** — upcoming subscribed events, past events attended, total hours volunteered (built on Milestone 2's check-in data)
- [ ] Feature: email notifications — signup confirmation, reminder before an event starts, cancellation notice if a promoter closes an event
- [ ] Feature: keyword search across event title/description (current filters are location/date/type only)
- [ ] Feature: favorite/bookmark an event without subscribing to it
- [ ] **New page: public event detail page** — viewable without login (read-only), prompting sign-in only when the user tries to subscribe — improves shareability
- [ ] **Milestone review**: confirm every task above is checked off; re-read the milestone against the current code and note anything that needs to change before moving to Milestone 4

## Milestone 4 — Ship it

Nothing is deployed yet; this milestone gets a real, reachable version live.

- [ ] Dockerize backend and frontend (`Dockerfile` for each, `docker-compose.yml` for local Postgres + backend + frontend)
- [ ] CI pipeline (GitHub Actions): run backend tests + frontend lint/tests on every push/PR
- [ ] Deploy: Postgres + backend to a host like Railway/Render/Fly.io, frontend to Vercel/Netlify
- [ ] Proper secrets management for `JWT_SECRET` and DB credentials in the hosting provider (no more manually-exported env vars)
- [ ] Basic error monitoring/structured logging (e.g. Sentry or just structured JSON logs) so failures in production are actually visible
- [ ] **Milestone review**: confirm every task above is checked off; re-read the milestone against the current code and note anything that needs to change before calling the roadmap done
