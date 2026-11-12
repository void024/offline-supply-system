# Offline-First Supply Inventory System

A field-warehouse supply reliability system that keeps working without internet:
storekeepers record incoming supplies, distribution and remaining stock offline;
devices sync on reconnect without duplicate entries or wrong stock counts.

Built for conditions like Manipur's, where connectivity is intermittent and stock
records must stay accurate across devices.

## Stack

- **Backend**: Java 21, Spring Boot 3, PostgreSQL, Flyway (`backend/`)
- **Frontend**: React 18, TypeScript, Vite, React Query, IndexedDB local-first sync (`frontend/`)
- **Deploy**: one Docker image serves both (frontend is embedded as static assets)

## How it works

- Append-only stock ledger: every receipt, issue and adjustment is an event with a
  client-generated `event_id`; the server deduplicates retries and quarantines
  conflicting events instead of corrupting balances.
- Local-first frontend: records are written to IndexedDB immediately, marked
  pending, and pushed on manual sync. Negative stock stays visible rather than
  being silently clamped.
- Supervisor review: discrepancies are resolved with a reason; resolutions are
  idempotent. Singleton issuer designation is enforced under concurrency.
- Device enrollment: first login from a device returns a one-time `device_key`;
  every API call needs the session cookie (SameSite=Strict, Secure) plus
  `X-Device-Id` / `X-Device-Key` headers.

## API

All routes are prefixed `/api/v1`. 10 endpoints: login/logout, event ingest,
sync pull, item balances, event history, discrepancy list/resolve, issuer
designate/status. Health: `/actuator/health`. See `backend/README.md` for the
full contract.

## Run locally

Backend (needs Java 21 + PostgreSQL 14+):

```bash
createdb -U postgres supply
cd backend
DB_URL=jdbc:postgresql://localhost:5432/supply DB_USER=postgres DB_PASSWORD=your-password \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile relaxes the cookie `Secure` flag for `http://localhost` and adds
two demo users (`keeper1`/`demo`, `boss1`/`demo`). Without `dev` or `test` the
server creates **no users** and fails closed; see `backend/README.md` for
provisioning the first supervisor.

Frontend (needs Node 20+), demo mode with sample data and no backend:

```bash
cd frontend
npm ci
npm run dev
```

## Tests

- Backend: 37 tests (13 unit + 24 integration against a real disposable
  PostgreSQL), covering concurrent pushes, response-loss retries, concurrent
  supervisor actions and session binding: `cd backend && ./mvnw test`
  (set `TEST_DB_URL` to a disposable database).
- Frontend: 17 tests (local store, sync guards, demo server, offline-mutation
  regression): `cd frontend && npm test`.

## Deploy (Docker)

```bash
docker build -t offline-supply .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host:5432/supply \
  -e DB_USER=... -e DB_PASSWORD=... \
  offline-supply
```

Flyway migrates the schema on first boot. Provision the first supervisor directly
in the database (bcrypt via pgcrypto), then log in through the web UI.

## Known limits

- Reconnection requires a manual **Sync now**; no background auto-sync.
- No installable offline shell (service worker): the app must already be open to
  record offline. Cold offline launch is not supported.
- Demo mode is sample data only and never touches the real API.
