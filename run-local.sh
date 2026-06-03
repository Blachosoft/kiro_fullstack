#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cleanup() {
  if [[ -n "${BACKEND_PID:-}" ]]; then
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
  if [[ -n "${FRONTEND_PID:-}" ]]; then
    kill "$FRONTEND_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "Starting backend on http://localhost:8080"
(
  cd "$ROOT_DIR/backend"
  mvn spring-boot:run
) &
BACKEND_PID=$!

echo "Starting frontend on http://localhost:3000"
(
  cd "$ROOT_DIR/frontend"
  mvn spring-boot:run
) &
FRONTEND_PID=$!

echo
echo "Open http://localhost:3000/login.html"
echo "Press Ctrl+C to stop both services."

wait
