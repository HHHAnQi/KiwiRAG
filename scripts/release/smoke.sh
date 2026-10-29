#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL is required}"
AUTH_TOKEN="${AUTH_TOKEN:?AUTH_TOKEN is required}"

curl -fsS "${BASE_URL}/actuator/health" >/dev/null
curl -fsS -H "Authorization: Bearer ${AUTH_TOKEN}" \
  "${BASE_URL}/api/v1/documents?size=1" >/dev/null

terminal_events=$(curl -fsSN -X POST "${BASE_URL}/api/v1/chat/sse" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"query":"release smoke test","top_k":1}' | grep -c '^event:done$' || true)

if [ "$terminal_events" -ne 1 ]; then
  echo "FAIL: expected exactly one SSE done event, got ${terminal_events}" >&2
  exit 1
fi

echo "PASS: health, authenticated document listing, and SSE terminal contract"
