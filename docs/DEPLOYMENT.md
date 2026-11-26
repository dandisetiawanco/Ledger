# Deployment Guide

Ledger ships with multi-stage non-root Dockerfiles (`infrastructure/docker/Dockerfile.backend`, `infrastructure/docker/Dockerfile.frontend`), `docker-compose.yml`, and an optional Nginx reverse proxy (`infrastructure/nginx/nginx.conf`).

## Production Configuration Checklist

1. **PostgreSQL Profile**: Set `SPRING_PROFILES_ACTIVE=postgres` and configure strong credentials via environment variables (never commit `.env`). Keep PostgreSQL bound to a private network; `docker-compose.yml` intentionally does not publish port `5432` externally.
2. **HTTPS & Secure Cookies**: Terminate TLS at a reverse proxy or load balancer and set `LEDGER_SESSION_COOKIE_SECURE=true` and `LEDGER_ALLOWED_ORIGINS=https://your-domain.example`.
3. **Demo Auto-Seed**: Set `LEDGER_DEMO_AUTO_SEED=false` in private production instances if you do not want a public demo organization seeded.
4. **Persistent Storage**: Mount durable volumes for PostgreSQL data (`/var/lib/postgresql/data`) and private document uploads (`LEDGER_STORAGE_DIR=/app/uploads`).
5. **Observability**: Monitor `/actuator/health/liveness` and `/actuator/health/readiness`. Metrics (`/actuator/metrics`, `/actuator/prometheus`) require `OWNER` or `ADMIN` authentication.
6. **Graceful Shutdown**: Spring Boot is configured with `server.shutdown: graceful` and a 20-second drain window so in-flight financial transactions finish committing before container termination.
