#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="$ROOT_DIR/.pids"
LOG_DIR="$ROOT_DIR/logs"
BACKEND_PORT="${SERVER_PORT:-8082}"
FRONTEND_PORT="${PORT:-3002}"
SPRING_PROFILE="${SPRING_PROFILES_ACTIVE:-h2}"

mkdir -p "$PID_DIR" "$LOG_DIR" "$ROOT_DIR/backend/data" "$ROOT_DIR/backend/uploads"

command -v java >/dev/null 2>&1 || { echo "Error: Java 21 is required." >&2; exit 1; }
command -v pnpm >/dev/null 2>&1 || { echo "Error: pnpm is required." >&2; exit 1; }

port_in_use() {
  lsof -i :"$1" -sTCP:LISTEN >/dev/null 2>&1
}

if [ -f "$PID_DIR/backend.pid" ] && kill -0 "$(cat "$PID_DIR/backend.pid")" 2>/dev/null; then
  echo "Ledger backend is already running (PID $(cat "$PID_DIR/backend.pid"))."
else
  if port_in_use "$BACKEND_PORT"; then
    echo "Error: Port $BACKEND_PORT is already in use by another process. Set SERVER_PORT to pick an alternative port." >&2
    exit 1
  fi
  echo "Starting Ledger backend on port $BACKEND_PORT (profile: $SPRING_PROFILE)..."
  (
    cd "$ROOT_DIR/backend"
    SERVER_PORT="$BACKEND_PORT" SPRING_PROFILES_ACTIVE="$SPRING_PROFILE" \
      nohup ./mvnw -B spring-boot:run -Dspring-boot.run.jvmArguments="-Xms256m -Xmx768m" \
      > "$LOG_DIR/backend.log" 2>&1 &
    echo $! > "$PID_DIR/backend.pid"
  )
fi

if [ -f "$PID_DIR/frontend.pid" ] && kill -0 "$(cat "$PID_DIR/frontend.pid")" 2>/dev/null; then
  echo "Ledger frontend is already running (PID $(cat "$PID_DIR/frontend.pid"))."
else
  if port_in_use "$FRONTEND_PORT"; then
    echo "Error: Port $FRONTEND_PORT is already in use by another process. Set PORT to pick an alternative port." >&2
    exit 1
  fi
  echo "Starting Ledger frontend on port $FRONTEND_PORT..."
  (
    cd "$ROOT_DIR/apps/web"
    BACKEND_URL="http://127.0.0.1:$BACKEND_PORT" PORT="$FRONTEND_PORT" \
      nohup pnpm exec next dev -p "$FRONTEND_PORT" \
      > "$LOG_DIR/frontend.log" 2>&1 &
    echo $! > "$PID_DIR/frontend.pid"
  )
fi

echo ""
echo "Ledger services launched:"
echo "  Frontend:  http://localhost:$FRONTEND_PORT"
echo "  Backend:   http://localhost:$BACKEND_PORT"
echo "  OpenAPI:   http://localhost:$BACKEND_PORT/swagger-ui.html"
echo "  Logs:      $LOG_DIR/"
echo "  Stop:      make stop"
