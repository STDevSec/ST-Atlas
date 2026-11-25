#!/usr/bin/env bash
# ST Atlas demo seed — loads a small, clearly-synthetic dataset through the REST API.
# For local demos and manual testing ONLY. Never run against a production instance.
#
#   ATLAS_URL=http://127.0.0.1:8080 \
#   ATLAS_ADMIN_USERNAME=admin ATLAS_ADMIN_PASSWORD=... \
#   ./scripts/demo-seed.sh
#
# The instance must already be set up (first-run wizard completed). Requires curl + jq.
set -euo pipefail

URL="${ATLAS_URL:-http://127.0.0.1:8080}"
USER="${ATLAS_ADMIN_USERNAME:?set ATLAS_ADMIN_USERNAME}"
PASS="${ATLAS_ADMIN_PASSWORD:?set ATLAS_ADMIN_PASSWORD}"
API="$URL/api/v1"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

# Spring Security SPA CSRF: fetch the XSRF-TOKEN cookie, echo it back in the header on writes.
csrf() { awk '/XSRF-TOKEN/ {print $7}' "$JAR" | tail -1; }
post() { # path json
  curl -sS -b "$JAR" -c "$JAR" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(csrf)" \
    -X POST "$API/$1" --data "$2";
}

echo "→ Authenticating as $USER at $URL"
curl -sS -c "$JAR" "$API/auth/csrf" >/dev/null
login_code="$(curl -sS -b "$JAR" -c "$JAR" -o /dev/null -w '%{http_code}' -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $(csrf)" -X POST "$API/auth/login" --data "{\"username\":\"$USER\",\"password\":\"$PASS\"}")"
[ "$login_code" = "200" ] || { echo "login failed ($login_code). Is the instance set up and the password correct?" >&2; exit 1; }

WS="$(curl -sS -b "$JAR" "$API/auth/me" | jq -r '.workspaces[0].id')"
[ -n "$WS" ] && [ "$WS" != "null" ] || { echo "no workspace found" >&2; exit 1; }
echo "  workspace: $WS"

echo "→ Creating demo client"
CLIENT="$(post "workspaces/$WS/clients" '{
  "name":"Acme Robotics (demo)","legalName":"Acme Robotics Demo S.A.","website":"https://acme-robotics.example",
  "notes":"Synthetic demo client — safe to delete.",
  "contacts":[{"name":"Dana Ops","email":"dana@acme-robotics.example","phone":null,"kind":"TECHNICAL"}]}' | jq -r '.id')"
echo "  client: $CLIENT"

echo "→ Creating demo assessment"
ASSESS="$(post "workspaces/$WS/assessments" "{
  \"title\":\"Acme Web & API Assessment (demo)\",\"typeCode\":\"WEB_PENTEST\",\"clientId\":\"$CLIENT\",
  \"startDate\":\"$(date -d '-7 days' +%F 2>/dev/null || date -v-7d +%F)\",\"dueDate\":\"$(date +%F)\",
  \"description\":\"Synthetic demo assessment for showcasing ST Atlas.\",
  \"objectives\":\"Demonstrate findings, CVSS and reporting.\",\"scope\":\"app.acme-robotics.example\",
  \"outOfScope\":\"Everything else.\",\"rulesOfEngagement\":\"Demo data only.\",
  \"methodology\":\"OWASP WSTG (illustrative).\",\"tags\":[\"web\",\"api\",\"demo\"]}" | jq -r '.id')"
echo "  assessment: $ASSESS"
curl -sS -b "$JAR" -c "$JAR" -H "X-XSRF-TOKEN: $(csrf)" -X POST "$API/workspaces/$WS/assessments/$ASSESS/transition" \
  -H "Content-Type: application/json" --data '{"status":"PLANNED"}' >/dev/null
curl -sS -b "$JAR" -c "$JAR" -H "X-XSRF-TOKEN: $(csrf)" -X POST "$API/workspaces/$WS/assessments/$ASSESS/transition" \
  -H "Content-Type: application/json" --data '{"status":"IN_PROGRESS"}' >/dev/null

echo "→ Creating demo findings"
post "workspaces/$WS/assessments/$ASSESS/findings" '{
  "title":"Reflected XSS in search parameter","description":"The q parameter is reflected without encoding.",
  "technicalDetails":"GET /search?q=<script>… executes in the victim browser.","impact":"Session or action hijacking.",
  "recommendation":"Context-aware output encoding + CSP.","references":["https://owasp.org/www-community/attacks/xss/"],
  "cvss31Vector":"CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N","cwe":["CWE-79"],"cve":[],"tags":["web","xss","demo"],
  "status":"OPEN","discoveredOn":"'"$(date +%F)"'"}' >/dev/null
post "workspaces/$WS/assessments/$ASSESS/findings" '{
  "title":"Missing security headers","description":"No HSTS / X-Content-Type-Options on responses.",
  "technicalDetails":"Response headers lack HSTS and nosniff.","impact":"Downgrade and MIME-sniffing exposure.",
  "recommendation":"Add HSTS, nosniff, a strict CSP.","references":["https://owasp.org/www-project-secure-headers/"],
  "severity":"LOW","cwe":["CWE-693"],"cve":[],"tags":["headers","demo"],"status":"OPEN","discoveredOn":"'"$(date +%F)"'"}' >/dev/null

echo "✓ Demo data seeded. Open $URL and sign in as $USER."
