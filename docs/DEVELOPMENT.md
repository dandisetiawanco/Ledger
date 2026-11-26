# Local Development Guide

Ledger provides two persistence profiles so the project runs comfortably on an 8 GB RAM laptop without requiring Docker, while supporting PostgreSQL in production-oriented environments.

## Prerequisites

- **Java 21** (OpenJDK / Temurin 21)
- **Node.js 22+** and **pnpm 12+**
- Maven is not required globally; use the included Maven Wrapper (`backend/mvnw`).
- Optional for PostgreSQL profile: PostgreSQL 16 or Docker Compose.

## Lightweight Profile (Default: H2 File-Backed)

1. Install dependencies once:
   ```bash
   make setup
   ```
2. Start backend (`:8082`) and frontend (`:3002`):
   ```bash
   make dev
   ```
3. Open:
   - Public site: `http://localhost:3002`
   - Interactive Demo: `http://localhost:3002/demo`
   - Backend API & Swagger UI: `http://localhost:8082/swagger-ui.html`
   - Health endpoint: `http://localhost:8082/actuator/health`
4. Stop only Ledger processes:
   ```bash
   make stop
   ```

The `scripts/dev.sh` launcher checks port availability before starting, tracks Ledger PIDs in `.pids/`, writes runtime logs to `logs/`, and caps backend heap (`-Xms256m -Xmx768m`) for low memory overhead.

## PostgreSQL Profile

Set environment variables from `.env.example` and start with `SPRING_PROFILES_ACTIVE=postgres`:

```bash
SPRING_PROFILES_ACTIVE=postgres \
POSTGRES_HOST=localhost \
POSTGRES_PORT=5432 \
POSTGRES_DB=ledger \
POSTGRES_USER=ledger \
POSTGRES_PASSWORD=change_me_local_only \
  ./backend/mvnw -f backend/pom.xml spring-boot:run
```

Or launch the full PostgreSQL + Backend + Frontend stack via Docker Compose:

```bash
docker compose up --build
```
