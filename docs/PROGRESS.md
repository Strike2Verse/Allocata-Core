# Progress

## Current position
- Phase 1: building the local Docker Compose stack on `chore/infra-postgres-service`
- Next: Redis service

## Phase 1: Workstation, Git and project skeleton
- [x] Checked tools: Java 21.0.12.1 (Temurin), Docker 29.9.2, Git 2.38.1, Node 20.11.1, VS Code 1.143.1
- [x] Verified the Docker engine with `hello-world`
- [x] Added `.gitignore` (PR #1)
- [x] Added README outline and problem statement (PR #2)
- [x] Added `.env.example` with placeholder settings
- [x] Added `.gitattributes` for consistent line endings
- [x] Docker Compose service: Postgres (16.15, healthcheck with `pg_isready`)
- [ ] Docker Compose service: Redis
- [ ] Docker Compose service: RabbitMQ
- [ ] Docker Compose service: PgBouncer
- [ ] Whole stack running and verified
- [ ] README: Getting Started

## Decisions
- The repo already existed on GitHub (Strike2Verse/Allocata-Core) with an MIT license.
- Folders, `.editorconfig` and similar files are added only when a phase needs them.
- Local passwords live only in `.env`, which Git ignores. The Postgres and
  RabbitMQ images create their users from it on first start.
