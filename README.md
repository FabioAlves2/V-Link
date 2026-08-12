# V-Link

Full-stack volunteering platform: promoters publish volunteer events, volunteers browse and subscribe to them.
Built with **Spring Boot 3.5.5 (Java 17)** + **PostgreSQL** (H2 in dev) on the backend, **React 19 + Vite + MUI** on the frontend.
Stateless JWT auth (access + revocable refresh tokens), Flyway-managed schema. UI copy is in Portuguese (pt-PT).

## Local development

### Backend (`backend/`)
Requires JDK 17. Runs against an in-memory H2 database by default — nothing to install.

```
JWT_SECRET=any-string-for-dev mvnw.cmd spring-boot:run   # Windows
JWT_SECRET=any-string-for-dev ./mvnw spring-boot:run     # Unix
```

Runs on `http://localhost:8080`. Tests (`mvnw.cmd test`) don't need `JWT_SECRET` set — the test profile overrides it.

**API docs (Swagger UI)**: `http://localhost:8080/swagger-ui/index.html` (OpenAPI JSON at `/v3/api-docs`). Login/register via `/auth/login`/`/auth/register` in the docs, copy the `token` from the response, then click **Authorize** and paste it in to try the protected endpoints.

### Frontend (`frontend/`)
```
npm install
npm run dev
```

Runs on `http://localhost:5173` — the only origin the backend's dev CORS config allows. There are no seeded accounts beyond what `BootSeed` creates on a fresh H2 boot (dev profile only) — register through the UI otherwise.

## Production-like local demo (Docker Compose)

No cloud hosting involved — this spins up Postgres + the backend (`prod` profile) + the built frontend (served via nginx), all on your machine, so you can demo the app the way it'd actually run in production.

```
cp .env.example .env   # fill in DB_PASSWORD / JWT_SECRET at minimum
docker compose up --build
```

- Frontend: `http://localhost:8081`
- Backend API: `http://localhost:8080` (health check at `/actuator/health`)

Notes:
- **Email is non-functional by design** in this demo — `MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD` default to placeholder values so the backend boots; real sends fail silently (`EmailService` swallows the error), same as with mail disabled in dev.
- **No demo accounts are seeded** under the `prod` profile (`BootSeed` only runs with `dev` active) — register a promoter and a volunteer account through the UI.
- Uploaded event images and Postgres data persist in named Docker volumes across `docker compose down`/`up` (not `docker compose down -v`).

## Architecture

Two independent apps talking only over HTTP: a Spring Boot REST API (JWT auth, Flyway migrations, Postgres/H2) and a React SPA (Vite, MUI, React Router). See `CLAUDE.md` for a full breakdown of package layout, security posture, and domain rules.

## Roadmap

See `plan.md` for the milestone-by-milestone development history.
