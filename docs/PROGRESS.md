# Progress

## Current position
- Phase 2 complete (on `docs/app-how-to-run`)
- Next: define Phase 3 (first real tables and features)

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

## Decisions
- The repo already existed on GitHub (Strike2Verse/Allocata-Core) with an MIT license.
- Domain: event ticketing. Allocata is the allocation engine, ticketing is its use case.
- Folders, `.editorconfig` and similar files are added only when a phase needs them.
- Local passwords live only in `.env`, which Git ignores. The Postgres and
  RabbitMQ images create their users from it on first start.
