# Progress

## Current position
- Phase 1 complete (finished on `docs/repo-getting-started`)
- Next: plan Phase 2

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

## Decisions
- The repo already existed on GitHub (Strike2Verse/Allocata-Core) with an MIT license.
- Domain: event ticketing. Allocata is the allocation engine, ticketing is its use case.
- Folders, `.editorconfig` and similar files are added only when a phase needs them.
- Local passwords live only in `.env`, which Git ignores. The Postgres and
  RabbitMQ images create their users from it on first start.
