# CLAUDE.md — working agreement for ST Atlas

ST Atlas — Security Assessment & Reporting Platform, developed by ST DevSec.
Self-hosted, local-first, Docker Compose, modular monolith.

## Ground rules

- **Spec-driven.** Specs live in `docs/product`, `docs/architecture`, `docs/security`.
  Change the spec first, then the code. Significant decisions get an ADR in `docs/adr/`.
- **Truthful status.** `docs/development/IMPLEMENTATION_STATUS.md` and the READMEs
  describe only what exists and was verified. Never mark a feature done without
  a passing test or a recorded manual verification. No fabricated screenshots,
  results, badges or claims.
- **Definition of Done** (spec §31): implemented, wired to the backend, persisted,
  validated, authorized, error/empty/loading states, tests passing, docs updated,
  security reviewed.
- **No external actions without authorization:** no `git push`, no image
  publishing, no releases, no GitHub repo creation, no paid resources. Commits
  only when the owner asks.
- **License pending.** Do not add a LICENSE file or license badges until ST DevSec approves one.
- **Branding.** No official ST DevSec logo is available: use the text wordmark
  (`docs/assets/branding/README.md`). Never invent a logo.

## Layout

| Path | What |
|---|---|
| `backend/` | Spring Boot 4.2, Java 21, Maven wrapper. Package root `com.stdevsec.atlas` |
| `frontend/` | Angular 22 (standalone, signals, zoneless), Tailwind 4, Tiptap 3, PDF.js |
| `infrastructure/nginx` | reverse proxy + static frontend + security headers |
| `compose.yaml` | production-style stack (localhost:8080) |
| `compose.dev.yaml` | dev overrides (DB port for local backend, etc.) |
| `scripts/` | backup, restore, update, health-check, demo-seed, screenshots |
| `docs/` | specs, architecture, security, development, deployment, user guide |

## Backend conventions

- One Spring Modulith module per top-level package under `com.stdevsec.atlas`.
  Other modules may only use a module's public API (top-level package types or
  `@NamedInterface` packages); `internal` sub-packages are private.
  `ModularityTests` enforces this — keep it green.
- REST under `/api/v1`. Workspace-owned resources are always addressed as
  `/api/v1/workspaces/{workspaceId}/...`; authorization is checked centrally
  (`WorkspaceAccess`) before any repository call. Every query filters by
  `workspace_id`.
- DTOs (records) at the edge, Bean Validation on every input, entities never
  serialized. Errors via `ProblemDetail` from the global handler; never leak
  stack traces or internal identifiers.
- Money/time: durations in seconds (`long`), timestamps `Instant` (UTC) in
  `timestamptz`.
- Migrations: Flyway `V{n}__{description}.sql`, never edit an applied migration.

## Frontend conventions

- Standalone components, signals for state, `OnPush`-equivalent zoneless change detection.
- All API calls through typed services in `src/app/core/api`; the CSRF token
  is sent automatically by `HttpClient` (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN`).
- No tokens in `localStorage`. Session = HttpOnly cookie.
- UI text through the i18n service (`en`, `es`); no hard-coded user-facing strings.
- Never render untrusted HTML; Tiptap JSON is rendered through Tiptap or the
  backend's escaping renderer.

## Commands

```bash
export JAVA_HOME=$HOME/.local/share/jdks/temurin-21   # JDK 21 (host default is 27)
cd backend && ./mvnw verify                             # backend tests (needs Docker for Testcontainers)
cd frontend && npm test && npm run build                # frontend unit tests + build
docker compose up -d --build                            # full stack on http://localhost:8080
```
