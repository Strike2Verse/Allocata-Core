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
_(coming soon)_

## Status
Under active development. See [docs/PROGRESS.md](docs/PROGRESS.md) for
what is done and what comes next.

## License
MIT. See [LICENSE](LICENSE).
