# Progress

## Current position
- Phase 2 complete
- Phase 3 complete
- Phase 4 complete
- Phase 5 planned (holds, orders and waitlist), nothing built yet
- Next: migration for the `waitlist_entries` table

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
- [x] Migration: `events` table (`tenant_id` foreign key, `name`, `venue`, `starts_at`, `status` as DRAFT, ON_SALE or CLOSED with a check, index on `tenant_id`) (`V4__create_events`, status defaults to DRAFT, `created_at` as `timestamptz`, `idx_events_tenant_id`)
- [x] Migration: `seats` table (`tenant_id` and `event_id` foreign keys, `label`, `price_cents` with a check of 0 or more, `status` as AVAILABLE, HELD or SOLD with a check, `version` for optimistic locking, unique `(event_id, label)`) (`V5__create_seats`, status defaults to AVAILABLE, `version` defaults to 0, `price_cents` as `integer`)
- [x] Indexes on `tenant_id` and `event_id` for the lookups the API will make (`V6__create_seat_indexes` adds `idx_seats_tenant_id`; no separate `event_id` index because the unique `(event_id, label)` constraint already has one that starts with `event_id`)
- [x] Test that foreign keys, status checks, negative prices and the unique seat label reject bad data (`SeatConstraintsTest`, 10 tests on a throwaway `postgres:16-alpine` container, checks the Postgres error code of each rejection: 23503 foreign key, 23514 check, 23505 unique; also proves a price of 0 and the same label in a different event are accepted)
- [x] Update this file with what was built and any decisions (Phase 4 built: `events`, `seats` and their indexes in V4 to V6, all rules enforced by the database and covered by tests; 12 tests pass in total; decisions are listed under Decisions)

## Phase 5: Holds, orders and waitlist
Hold is the lease, order is the checkout. All three tables are plain data with rules enforced by the database; the hold logic, expiry and promotion come in later phases.
- [x] Migration: `seat_holds` table (`tenant_id`, `seat_id` and `user_id` foreign keys, `status` as ACTIVE, EXPIRED, RELEASED or CONVERTED with a check, `expires_at`, `created_at`) (`V7__create_seat_holds`, status defaults to ACTIVE)
- [x] Partial unique index so a seat has at most one ACTIVE hold, plus an index on `expires_at` for the later expiry sweep (`V8__create_seat_hold_indexes`, `uq_seat_holds_active_seat` and `idx_seat_holds_expires_at`)
- [x] Test that a second active hold on the same seat is rejected, while a new hold is accepted after the first one is released (`SeatHoldConstraintsTest`, 5 tests on a throwaway `postgres:16-alpine` container: first active hold accepted, second active hold on the same seat rejected with 23505, new active hold accepted after the first is released, active holds on different seats accepted, many EXPIRED, RELEASED and CONVERTED holds on one seat accepted)
- [x] Migration: `orders` table (`tenant_id`, `user_id`, `seat_id` and `hold_id` foreign keys, `price_cents` with a check of 0 or more, `status` as PENDING, PAID or CANCELLED with a check, unique `hold_id` so one hold makes one order) (`V9__create_orders`, status defaults to PENDING, `price_cents` as `integer`, `created_at` as `timestamptz`)
- [x] Test that foreign keys, status checks, negative prices and a second order for the same hold reject bad data (`OrderConstraintsTest`, 9 tests on a throwaway `postgres:16-alpine` container: valid order accepted, unknown tenant, user, seat or hold rejected with 23503, unknown status and negative price rejected with 23514, second order for the same hold rejected with 23505, price of 0 accepted)
- [ ] Migration: `waitlist_entries` table (`tenant_id`, `event_id` and `user_id` foreign keys, `status` as WAITING, PROMOTED or CANCELLED with a check, unique `(event_id, user_id)` so one spot per user per event)
- [ ] Test that a user cannot join the same event waitlist twice
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
- Phase 5 scope: one hold covers one seat and one order covers one hold, so buying several
  seats means several orders for now. A cart or order with many items is deferred. The waitlist
  is per event, not per seat, like the library waitlist was per title. `seats.status` stays as
  the durable record while `seat_holds` keeps the history of who held what and when.
- Order `status` has no REFUNDED value yet; refunds and the payment ledger arrive in Phase 30.
- The seat count of an event is derived from the `seats` rows, not stored on `events`, so it
  can never drift from the real seats. `tenant_id` is repeated on `seats` so tenant checks
  need no join.
