# supply-server

The supply-server from the Prompt 2 architecture contract: an offline-first supply
reliability system for field warehouses with intermittent connectivity. Java 21,
Spring Boot 3, PostgreSQL, Flyway. Implements exactly the 10 contract endpoints and
the 9-table contract schema (plus the documented `sessions` implementation table).

## Prerequisites

Check these before anything else:

- **Java 21** - run `java -version`; it must print `21.x` (Temurin recommended:
  https://adoptium.net/temurin/releases/?version=21)
- **PostgreSQL 14+ installed and running** (https://www.postgresql.org/download/windows/) -
  the server does not bring its own database. Docker Desktop with the Compose file
  below is the alternative.
- **No Maven install needed** - the Maven wrapper is included (`mvnw.cmd` on Windows,
  `./mvnw` elsewhere). Use it for every Maven command below.
- Docker is NOT required for the normal local run.

## Run locally (Windows + PowerShell, no Docker)

One time, with PostgreSQL running:

```powershell
createdb -U postgres supply
```

Then, from the `offline-supply-backend/` folder:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/supply"
$env:DB_USER="postgres"            # your PostgreSQL user
$env:DB_PASSWORD="your-password"   # that user's password
.\mvnw.cmd spring-boot:run -D"spring-boot.run.profiles=dev"
```

The `dev` profile does two things: it relaxes ONLY the cookie `Secure` flag for
same-laptop `http://localhost` testing (the contract's one permitted relaxation),
and it applies the development-only seed users (`keeper1` / `demo`, storekeeper;
`boss1` / `demo`, supervisor) so a fresh checkout is immediately reviewable.

## Development seeds and the fail-closed default

Flyway migrations live in two locations:

- `db/migration/` - always applied. Schema plus the six contract seed items only.
- `db/dev-seed/` - applied ONLY under the `dev` and `test` profiles. Holds the two
  demo users (password `demo`, BCrypt strength 12).

A launch without `dev` or `test` therefore creates **no users at all**: login is
impossible and the service fails closed instead of shipping a known password.

## Provisioning the first supervisor (non-dev launch)

The contract defines no user-management endpoint, so the first account is created
out of band, straight in the database. With the server stopped (or before first
launch), against the `supply` database:

```sql
create extension if not exists pgcrypto;

insert into users (id, username, password_hash, role)
values (gen_random_uuid(), 'boss', crypt('choose-a-strong-password', gen_salt('bf', 12)), 'supervisor');
```

`crypt(..., gen_salt('bf', 12))` produces a BCrypt hash that Spring Security
verifies directly, so no extra tooling is needed. The first login from any device
then enrolls that device and returns its one-time `device_key`, exactly as in dev.

## Run tests

Integration tests need a second, disposable real PostgreSQL database (they wipe and
re-migrate it; never point `TEST_DB_URL` at real data):

```powershell
createdb -U postgres supply_test
$env:TEST_DB_URL="jdbc:postgresql://localhost:5432/supply_test"
$env:DB_USER="postgres"
$env:DB_PASSWORD="your-password"
.\mvnw.cmd test
```

37 tests: 13 unit + 24 integration against real PostgreSQL, including the
concurrency guarantees (simultaneous pushes, response-loss retries, concurrent
designation, concurrent resolution) and the session-binding checks.

## Run with Docker Compose

```powershell
echo "DB_PASSWORD=change-me" > .env
docker compose up --build
```

Note: the Docker build was not exercised in the authoring environment (no Docker
daemon there). First run on your machine is the real check.

## Configuration (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/supply` | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `postgres` / `postgres` | database credentials (no defaults work unless your local install matches; set them explicitly) |
| `TEST_DB_URL` | `jdbc:postgresql://localhost:5432/supply_test` | disposable test database |
| `SERVER_PORT` | `8080` | HTTP port |
| `SESSION_COOKIE_NAME` | `SUPPLY_SESSION` | session cookie name |
| `SESSION_TTL_HOURS` | `24` | session lifetime |
| `SESSION_SECURE_COOKIE` | `true` | cookie `Secure` flag (relaxed only by the dev profile) |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_WINDOW_SECONDS` | `5` / `60` | login guard |
| `SYNC_MAX_BATCH_SIZE` / `SYNC_MAX_PULL_LIMIT` | `500` / `500` | sync limits |

## API

All routes are prefixed `/api/v1` and (except login) require the session cookie plus
`X-Device-Id` + `X-Device-Key` headers. The 10 endpoints:

1. `POST /auth/login` - rate limited; first login enrolls the device and returns `device_key` once
2. `POST /auth/logout` - idempotent
3. `POST /events` - the only write path; per-event ACCEPTED/DUPLICATE/CONFLICT/QUARANTINED
4. `GET /sync/pull?after={seq}&limit=500` - events page + consistent reference snapshot
5. `GET /items` - computed balances (negative allowed) + planned outstanding
6. `GET /events?item_id=&type=&after=&limit=` - read-only history browse
7. `GET /discrepancies?status=OPEN`
8. `POST /discrepancies/{id}/resolve` - supervisor; idempotent on `resolution_id`
9. `POST /issuer/designate` - supervisor; singleton; 409 if already designated
10. `GET /issuer` - current designation or nulls

Health: `/actuator/health`.

## Layout

```
mvnw, mvnw.cmd, .mvn/wrapper/    Maven wrapper (no Maven install needed)
src/main/java/com/offlinesupply/server/
  config/       typed properties, security chain
  api/          controllers, DTO records, exception advice
  service/      ingest, sync, items, discrepancies, issuer, reconciliation, auth
  repository/   Spring Data JPA repositories
  domain/       10 entities + enums with DB-value converters
  security/     session cookie + device header filter, hashing
  exception/    ApiException
src/main/resources/
  db/migration/V001__init.sql    9 contract tables + sessions + 6 seed items
  db/dev-seed/V101__dev_users.sql  dev/test-only demo users
```
