#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
export STORAGE_ROOT="${STORAGE_ROOT:-$ROOT/storage}"
export WORKER_BASE_URL="${WORKER_BASE_URL:-http://127.0.0.1:8001}"
export WHISPER_MODEL="${WHISPER_MODEL:-tiny}"
export WHISPER_DEVICE="${WHISPER_DEVICE:-cpu}"
export WHISPER_COMPUTE="${WHISPER_COMPUTE:-int8}"
mkdir -p "$STORAGE_ROOT"

PYTHON_BIN="${PYTHON_BIN:-python3}"
if [[ -x "$ROOT/worker/venv/bin/python" ]]; then
  PYTHON_BIN="$ROOT/worker/venv/bin/python"
elif [[ -x "$ROOT/worker/.venv/bin/python" ]]; then
  PYTHON_BIN="$ROOT/worker/.venv/bin/python"
fi

echo "Starting Python worker on :8001"
cd "$ROOT/worker"
"$PYTHON_BIN" -m uvicorn main:app --host 127.0.0.1 --port 8001 &
WORKER_PID=$!

echo "Starting Spring Boot on :8080"
cd "$ROOT/backend"
mvn -q spring-boot:run -DskipTests &
BACKEND_PID=$!

echo "Starting React frontend on :5173"
cd "$ROOT/frontend"
if [[ ! -d node_modules ]]; then
  npm install
fi
npm run dev &
FRONTEND_PID=$!

cleanup() {
  kill "$WORKER_PID" "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

wait
