# Allocata Core

A backend leasing engine that allocates limited assets under heavy
concurrent demand: exact allocation, short-lived holds, fair waitlists,
reliable event delivery and a balanced financial ledger.

## Problem Statement

When many users request the same scarce asset at the same moment, naive
systems break in predictable ways. Allocata Core is built to handle each one:

| Problem | What goes wrong | Approach |
|---------|-----------------|----------|
| Double booking | Two requests both see "1 left" and both succeed | Atomic check-and-claim in a Redis Lua script |
| Dual write | Data is saved but the follow-up event is lost in a crash | Transactional outbox with a relay |
| Cascading failure | A slow external provider blocks every worker | Circuit breaker, retries and a dead-letter queue |
| Money drift | Concurrent balance updates collide or double-charge | Immutable double-entry ledger |
| Retry duplicates | A timeout or double-click repeats the same action | Idempotency-Key header |
| Overload | One client floods the API | Rate limiting and connection pooling |

**Example:** an asset has 5 units and 1,000 users claim it at once.
Exactly 5 receive a 10-minute lease, the rest are told it is unavailable
and may join a waitlist. Unconfirmed leases expire and return to stock.

> This list reflects the current scope. It will evolve as the project
> develops: new problems may be added, approaches refined, and items
> removed if they stop adding value.

## Architecture
_(coming soon)_

## Tech Stack
Java 21 · Spring Boot · PostgreSQL 16 · Redis 7 · RabbitMQ · PgBouncer · Docker Compose

## Getting Started

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
  (or Docker Engine with Compose v2)
- [Git](https://git-scm.com/)

### 1. Configure
```sh
git clone https://github.com/Strike2Verse/Allocata-Core.git
cd Allocata-Core
cp .env.example .env
```
Replace every `change_me` in `.env` with your own password. `.env` is
ignored by Git and must never be committed.

PostgreSQL and RabbitMQ create their users from `.env` on first start only.
To change a password later, reset the data with `docker compose down -v`.

### 2. Start the stack
```sh
docker compose up -d
docker compose ps
```
After about 30 seconds every service shows `(healthy)`. PgBouncer waits
until PostgreSQL is healthy before it starts.

Quick checks:
```sh
docker exec -it allocata-pgbouncer psql -h localhost -p 6432 -U allocata -d allocata -c "select version();"
docker exec -it allocata-redis redis-cli ping
```
The first asks for the PostgreSQL password from `.env` and prints the
version. The second prints `PONG`.

### Local services
| Service | Port | Use |
|---------|------|-----|
| PostgreSQL | 5432 | Direct database access (admin, migrations) |
| PgBouncer | 6432 | Pooled database connections, used by the app |
| Redis | 6379 | Cache, rate limits and short-lived locks |
| RabbitMQ | 5672 | Messaging (AMQP) |
| RabbitMQ UI | 15672 | Management dashboard at http://localhost:15672, log in with the RabbitMQ user from `.env` |

### 3. Stop and clean up
```sh
docker compose stop      # pause, keep containers and data
docker compose down      # remove containers, keep data in volumes
docker compose down -v   # remove containers and all data
```
To remove the downloaded images as well, use
`docker compose down -v --rmi all`.

## Status
Under active development, built step by step in phases.

**Current phase:** Phase 1, workstation and project skeleton. The local
Docker Compose stack (PostgreSQL, PgBouncer, Redis, RabbitMQ) runs and is
verified healthy. No application code yet.

The full checklist, the current step and the decisions made along the way
live in [docs/PROGRESS.md](docs/PROGRESS.md).

## License
MIT. See [LICENSE](LICENSE).
