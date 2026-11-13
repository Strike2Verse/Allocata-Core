# Progress

## Current position
- Phase 2 complete
- Phase 3 complete
- Phase 4 planned (events and seats)
- Next: migration for the `events` table

## Phase 1: Workstation, Git and project skeleton
- [x] Checked tools: Java 21.0.12.1 (Temurin), Docker 29.9.2, Git 2.38.1, Node 20.11.1, VS Code 1.143.1
- [x] Verified the Docker engine with `hello-world`
- [x] Added `.gitignore` (PR #1)
- [x] Added README outline and problem statement (PR #2)
- [x] Added `.env.example` with placeholder settings
- [x] Added `.gitattributes` for consistent line endings
- [x] Docker Compose service: Postgres (16.15, healthcheck with `pg_isready`)
- [x] Docker Compose service: Redis (7.4.12, AOF persistence, healthcheck with `redis-cli ping`)
- [x] Docker Compose service: RabbitMQ (4.4.0 with management UI on 15672, fixed hostname, healthcheck with `rabbitmq-diagnostics ping`)
- [x] Docker Compose service: PgBouncer (1.26.1 on 6432, transaction pooling, starts after healthy Postgres, healthcheck with `pg_isready`)
- [x] Whole stack running and verified (fresh `down -v` + `up -d`, all 4 services healthy)
- [x] README: Getting Started (prerequisites, env setup, start and checks, ports, cleanup)

## Phase 2: Spring Boot app connected to the stack
- [x] Spring Boot skeleton (Spring Boot 4.1.2, Java 21, Maven wrapper 3.10.0, Web, Actuator health on 8080)
- [x] Configuration from environment variables (`.env` imported by Spring, `SERVER_PORT` with 8080 default, no secrets committed)
- [x] Database connection through PgBouncer (6432) (JDBC and Hikari pool, `prepareThreshold=0`, health `db` is `UP`, goes `DOWN` when PgBouncer stops)
- [x] Flyway migrations directly on Postgres (5432), empty baseline (`V1__baseline` applied, `flyway_schema_history` has 1 row, separate Flyway connection bypasses PgBouncer)
- [x] Redis and RabbitMQ connections, all health checks `UP` (Redis and AMQP starters, host and port from env with localhost defaults, RabbitMQ credentials reused from `.env`, health shows `db`, `redis` and `rabbit` as `UP`)
- [x] README: how to run the app (Java 21 prerequisite, run with the Maven wrapper, health check and Flyway history check)

## Phase 3: Database basics, tenants and users
Flyway itself was set up in Phase 2, so this phase starts from the empty baseline.
- [x] Automated test that all migrations run on an empty database (Testcontainers with `postgres:16-alpine`, same image as compose, Flyway runs directly on a throwaway container, checks the applied count matches the migration files, nothing pending and no failed rows)
- [x] Migration: `tenants` table (organizers that own events) (`V2__create_tenants`, uuid primary key with `gen_random_uuid()`, `name`, unique `slug`, `created_at` as `timestamptz`)
- [x] Migration: `users` table with `tenant_id`, role (ADMIN or BUYER) and a unique email per tenant (`V3__create_users`, foreign key to `tenants`, role as `text` with a check, unique `(tenant_id, email)`, no password hash until the auth phase, email case handled in the app later)

## Phase 4: Events and seats
Event is the asset and seat is the item. Both tables are plain data; holds and orders come in Phase 5.
- [ ] Migration: `events` table (`tenant_id` foreign key, `name`, `venue`, `starts_at`, `status` as DRAFT, ON_SALE or CLOSED with a check, index on `tenant_id`)
- [ ] Migration: `seats` table (`event_id` foreign key, `label`, `price_cents`, `status` as AVAILABLE, HELD or SOLD with a check, unique `(event_id, label)`)
- [ ] Test that foreign keys, check constraints and the unique seat label reject bad data
- [ ] Update this file with what was built and any decisions

## Decisions
- The repo already existed on GitHub (Strike2Verse/Allocata-Core) with an MIT license.
- Domain: event ticketing. Allocata is the allocation engine, ticketing is its use case.
- The original plan used a library lending example. It is translated to ticketing:
  asset becomes event, item becomes seat, lease becomes a 10-minute seat hold,
  checkout becomes an order, return becomes a cancellation or refund, and the
  fine ledger becomes a payment ledger. Rules with no ticketing equivalent
  (overdue states, the daily fines job) are redesigned when their phase comes.
- Flyway was set up in Phase 2, one phase earlier than planned. Applied migrations
  are never edited; changes go in a new version.
- Tests use the same Postgres image as compose (`postgres:16-alpine`). The Debian-based
  `postgres:16.15` image rejected this machine's `Asia/Calcutta` time zone name.
- Folders, `.editorconfig` and similar files are added only when a phase needs them.
- Local passwords live only in `.env`, which Git ignores. The Postgres and
  RabbitMQ images create their users from it on first start.
- Phase 4 scope: one price per seat (`price_cents`, integer, no floating point money), seat
  `status` lives on `seats` for now, and general admission (tickets without seat numbers)
  is deferred. If Redis becomes the source of truth for holds in Phase 11, the column stays
  as the durable record.
