# V-Link

Volunteering platform for Portugal. Promoters publish volunteer events; volunteers
browse, subscribe, and get credited for the hours they actually showed up for.

Personal project, built solo. Spring Boot API + React SPA, running against Postgres
under Docker Compose.

## What it does

**Two roles.** Promoters create, publish, close and cancel events, see who signed up,
mark attendance, and export the subscriber list to CSV. Volunteers browse published
events, subscribe, favourite, and track their accumulated volunteering hours.

**Event lifecycle.** `DRAFT → PUBLISHED → CLOSED`, with transitions enforced as a
state machine rather than a free choice of enum values. Closing requires the event to
have started and is permanent; cancelling only applies before it starts and hard-deletes
the event, its subscriptions and its favourites — notifying everyone affected first.

**Capacity, enforced properly.** Subscribing takes a pessimistic row lock rather than
counting-then-inserting, because counting first oversubscribes under concurrency
(see below).

**Attendance and hours.** Promoters check subscribers in; only checked-in past
subscriptions count toward a volunteer's total hours. Signing up without being
confirmed contributes zero.

**Notifications and email.** In-app notifications on closure, cancellation and
rescheduling, plus best-effort email for the same events and a scheduled reminder for
events starting within the next 24 hours, deduplicated per subscription.

**Also:** debounced keyword/location/date/type search, event image upload, favourites
as a separate no-commitment bookmark, and full OpenAPI docs.

## Stack

**Backend** — Spring Boot 3.5.5 · Java 17 · Spring Security (JWT) · Spring Data JPA ·
Flyway · Postgres 16 (prod) / H2 (dev) · springdoc-openapi · Maven

**Frontend** — React 19 · Vite 7 · MUI 7 · Vitest + React Testing Library

**Infra** — Docker Compose (Postgres + backend + nginx-served frontend) ·
Spring Boot Actuator healthchecks

## Running it

```bash
# Production-like: Postgres, prod profile, real Flyway migrations, built frontend
cp .env.example .env   # fill in DB_PASSWORD / JWT_SECRET at minimum
docker compose up --build
# frontend  http://localhost:8081
# API       http://localhost:8080
# Swagger   http://localhost:8080/swagger-ui/index.html
```

- **Email is non-functional by design** in this demo — `MAIL_HOST`/`MAIL_USERNAME`/
  `MAIL_PASSWORD` default to placeholder values so the backend boots; real sends fail
  silently (`EmailService` swallows the error), same as with mail disabled in dev.
- **No demo accounts are seeded** under the `prod` profile — register a promoter and a
  volunteer account through the UI.
- Uploaded event images and Postgres data persist in named Docker volumes across
  `docker compose down`/`up` (not `docker compose down -v`).

```bash
# Dev: in-memory H2, hot reload, nothing to install beyond a JDK
cd backend  && JWT_SECRET=<any-long-string> ./mvnw spring-boot:run   # Unix
cd backend  && set JWT_SECRET=<any-long-string> && mvnw.cmd spring-boot:run   # Windows
cd frontend && npm install && npm run dev     # http://localhost:5173
```

The schema is owned by Flyway, not Hibernate — both profiles run `ddl-auto: validate`.
Secrets come from the environment; nothing is committed. Tests (`mvnw.cmd test` /
`npm run test`) need no setup — the backend's test profile overrides `JWT_SECRET`, and
H2 requires nothing to install.

In Swagger UI, call `/auth/login` or `/auth/register`, copy the `token` field, click
**Authorize**, and paste it in to try a protected endpoint.

## Auth model

Typed JWTs: a 15-minute access token and a 7-day refresh token, each carrying its role,
type and a `jti`. The auth filter accepts only access tokens and `/auth/refresh` only
refresh tokens, so neither can be used as the other. Refresh tokens are persisted per
`jti` and therefore revocable — rotated on every refresh, revoked on logout. Access
tokens are deliberately not revocable: no per-request database lookup, which is the
tradeoff stateless JWTs exist to make.

The frontend transparently refreshes on a 401 and queues in-flight requests while it
does. Login is rate-limited per email; registration rate-limits only the duplicate-email
failure branch, per IP, so legitimate signups never trip it.

## Problems worth reading about

**Capacity was oversubscribable.** A count-then-insert check let 8 concurrent requests
all fit into an event with capacity 1 — reproduced, not theorised. Fixed with a
`SELECT ... FOR UPDATE` on the event row. The same read-check-then-write shape shows up
anywhere a limit is enforced against a live count.

**A Postgres error the test suite could never see, twice.** `findByFilters` takes four
nullable parameters. Postgres couldn't infer a type for parameters used only in
`:param IS NULL` checks, and once the same SQL ran enough times, pgjdbc switched to a
server-side prepared statement whose Describe step demands concrete types — throwing
`could not determine data type of parameter $n`. The first fix cast only the parameters
inside `LOWER(CONCAT(...))`, which fixed the error I'd seen and shipped a latent one on a
different filter. The lesson was to cast *every* occurrence of a nullable parameter and
verify by firing the query past the prepare threshold. H2 has no equivalent behaviour,
so no test could have caught either.

**Every date was an hour early during summer time.** Dates are `LocalDateTime` — server
wall-clock, no timezone — and the frontend was sending them via `.toISOString()`, which
converts to UTC. Under WEST (UTC+1) that shifted everything back an hour, making
near-future events read as already past.

**A 403 that should have been a 401.** Spring Security's default entry point returns 403
for a missing or expired token, which silently broke the frontend's refresh-on-401
interceptor: every session past 15 minutes got permanently stuck. Fixed with an explicit
`authenticationEntryPoint`, leaving genuine wrong-role 403s untouched.

**Docker volumes and a non-root user.** A named volume mounting onto a directory the
image never created gets made `root:root` on first start, and the non-root `spring` user
then can't write to it — image uploads failed exactly this way, in the container only.
Rebuilding the image doesn't fix a volume that already exists; the volume has to be
removed.

**A route I deleted rather than fixed.** An early `/api/users` endpoint deserialised a
raw `User` straight from the request body with no role check, letting any authenticated
user assign themselves an arbitrary role or a pre-hashed password. It's gone, and
`EventController.create` now strips any client-supplied `id` before saving, since
`save()` on an attacker-chosen id becomes a `merge()` that bypasses ownership checks.

## Testing

Backend tests are full-context `@SpringBootTest` driven through `MockMvc` against real
H2 — HTTP in, HTTP out, no mocked service layers. Concurrency tests use
`ExecutorService` + `CountDownLatch` and assert the durable invariant (the final row and
notification counts) rather than which request won, because that split is timing-
dependent. Frontend tests cover the pages, the axios refresh interceptor and the CSV
utility.

## Known limitations

Honest list, since every one of these was a decision rather than an oversight:

- Tokens live in `localStorage`. No XSS vector exists in the app today, but that's a
  property of the current code, not a guarantee.
- Image upload validates the declared `Content-Type`, not the file bytes. Acceptable
  while the app only serves its own uploads statically; not a pattern to reuse.
- `ApiExceptionHandler` classifies unique-constraint violations by matching against the
  database's error message. It works, and the ordering is documented, but a new
  constraint requires care rather than confidence.
- Nothing automatically closes an event once its end date passes, so `PUBLISHED` does
  not mean "still happening" — the date filter carries that weight instead.
- Not deployed. Runs locally under Docker Compose.

## Further reading

- [`CLAUDE.md`](CLAUDE.md) — full architecture, security posture, and domain-rule
  documentation.
- [`plan.md`](plan.md) — the build log this project was actually developed against,
  including the reasoning and tradeoffs behind each feature.
