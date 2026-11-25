#!/usr/bin/env bash
# ST Atlas health check — container status + HTTP reachability of the app perimeter.
#   ./scripts/health-check.sh
# Exit code is non-zero if any check fails (usable in CI / cron).
set -uo pipefail

cd "$(dirname "$0")/.."
PORT="${ATLAS_PORT:-8080}"
HOST="${ATLAS_BIND_ADDRESS:-127.0.0.1}"
BASE="http://$HOST:$PORT"
fail=0

echo "== Containers =="
docker compose ps --format '  {{.Service}}\t{{.Status}}' || fail=1

check() { # name url expected-code
  local code; code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "$2" 2>/dev/null || echo 000)"
  if [ "$code" = "$3" ]; then echo "  ✓ $1 ($code)"; else echo "  ✗ $1 (got $code, want $3)"; fail=1; fi
}

echo "== HTTP =="
check "web healthz"        "$BASE/healthz"                 200
check "SPA index"          "$BASE/"                        200
check "API setup status"   "$BASE/api/v1/setup/status"     200
# These MUST NOT be reachable through the edge:
check "actuator blocked"   "$BASE/actuator/health"         404

echo
if [ "$fail" = 0 ]; then echo "All checks passed."; else echo "One or more checks FAILED." >&2; fi
exit "$fail"
