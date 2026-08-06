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

- [ ] **New page: Organizer Dashboard** — list of events created by the logged-in promoter, with subscriber counts and quick edit/close links
- [ ] **New page: Event Subscribers view** — per-event list of volunteers who signed up (name/email), with CSV export
- [ ] Feature: event image upload (replace the current URL-only field) — needs a storage backend (local disk to start, or S3-compatible later)
- [ ] Feature: close/cancel an event, notifying already-subscribed volunteers
- [ ] Feature: attendance/check-in tracking — mark which subscribers actually showed up (foundation for volunteer-hours tracking in Milestone 3)
- [ ] **Milestone review**: confirm every task above is checked off; re-read the milestone against the current code and note anything that needs to change before moving to Milestone 3

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
