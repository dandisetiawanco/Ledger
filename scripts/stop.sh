#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="$ROOT_DIR/.pids"

stop_pid_file() {
  local name="$1"
  local file="$PID_DIR/$name.pid"
  if [ -f "$file" ]; then
    local pid
    pid="$(cat "$file")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "Stopping Ledger $name (PID $pid)..."
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$file"
  else
    echo "No tracked Ledger $name process."
  fi
}

stop_pid_file "frontend"
stop_pid_file "backend"
