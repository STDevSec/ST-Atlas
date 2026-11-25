# Implementation status

Truthful, dated record. "Verified" = an executed test or a recorded manual check.

_Last update: 2026-11-24 (session 2)._

## Completed

### Specifications
- B-001 repository skeleton; specs (PRD, requirements, architecture, modules, data model, API design, threat model); ADRs 0001–0010; backlog.

### Backend (Spring Boot 4.2, Java 21) — all modules build and are covered by tests
- identity (auth, sessions, Argon2, rate limiting, lockout, TOTP MFA, admin users), workspace (roles, path-scoped access, isolation), system (first-run setup token), client, assessment (lifecycle + transitions), finding (CVSS 3.1 server-side, CWE/CVE), storage (content-typed uploads, MIME inspection), report (Tiptap-JSON allow-list renderer → Gotenberg PDF, immutable finalized versions), search (Postgres FTS en/es + pg_trgm, tag filters, ts_headline highlight), timetracking (manual + timer, CSV with formula-injection guard), audit (append-only), dashboard, shared.
- Tests verified: 33 unit + 12 integration (real PostgreSQL + real Gotenberg via Testcontainers). ModularityTests green. Added `ReportRendererTest` newline/line-break regression (8/8).

### Frontend (Angular 22, zoneless, signals) — all routed pages implemented and verified in the browser
- Auth/setup: login, first-run setup. Shell + nav + i18n (en/es) + light/dark theme.
- dashboard, clients (+ form w/ contacts & logo), assessments (list/cards, filters, form, detail w/ tabs, lifecycle), findings (workspace list + form w/ live CVSS), reports list, report studio (Tiptap: paste/drop image upload, autosave w/ optimistic-revision conflict handling, insert finding / findings-table / page-break), pdf-viewer (PDF.js: zoom, page nav, in-doc search, download, print).
- search (full-text + tag cloud, highlighted snippets), time tracking (timer start/stop + manual entry + CSV export), templates (built-in + custom, config editor, duplicate/export/import), settings (profile, language, theme, password, MFA setup/enable/disable, members + roles, create user/workspace), audit log (paginated, readable actions).
- Frontend builds clean (`ng build`). All 5 remaining pages manually verified end-to-end in Chrome (no console errors); search highlighting and timer start/stop exercised against the live backend.

### Verified end-to-end (browser, real backend + real Gotenberg)
- Full vertical slice: client → assessment → findings (CVSS auto) → report studio (text + inserted findings + evidence images) → PDF generation → finalized/locked version. Two real reports produced (a demo pentest, and a real non-destructive assessment of the Sellax production surface, ATL-2026-0003, 7-page finalized PDF).

### Full-stack Docker — verified
- `docker compose up -d --build` brings up secrets-init → db → gotenberg → backend → web, all reaching healthy. Verified on an isolated project (`-p st-atlas-prod`): web on `127.0.0.1:8080`; backend/db/gotenberg on internal networks only (Postgres not reachable from the host); nginx serves the SPA and proxies `/api`, blocks `/actuator` (404); strong CSP + nosniff + XFO present. First-run setup wizard → login → dashboard verified through the composed stack; styled correctly; server-computed CVSS shown.
- Ops scripts written and verified: `scripts/backup.sh` (pg_dump + storage tar), `scripts/restore.sh` (full backup→restore cycle tested: data intact after restore), `scripts/health-check.sh` (container + HTTP perimeter checks, exit code), `scripts/demo-seed.sh` (CSRF+login, creates synthetic client/assessment/findings via the API — run against the composed stack successfully).
- Build note: the dev host runs Docker 29 without the buildx plugin, so the BuildKit `--mount=type=cache` lines in the Dockerfiles need `docker-buildx` installed (`sudo pacman -S docker-buildx`). The images were built on this host with the classic builder (cache mounts stripped) to verify; the committed Dockerfiles keep the BuildKit directives (correct for CI).

## Pending
- Playwright e2e. (Real screenshots captured: 12 in `docs/assets/screenshots`, from the composed stack with synthetic demo data, used in the READMEs.)
- `scripts/update.sh` (pull/rebuild/migrate helper).
- Docs: USER_STORIES, ACCEPTANCE_CRITERIA, SECURITY_CONTROLS, HARDENING, DATA_CLASSIFICATION, DEVELOPMENT_GUIDE, CODING_STANDARDS, TESTING_STRATEGY, INSTALLATION, BACKUP_RESTORE, TROUBLESHOOTING, USER_GUIDE, DESIGN_SYSTEM; CHANGELOG, CONTRIBUTING, SECURITY, CODE_OF_CONDUCT, ROADMAP, THIRD_PARTY_NOTICES. (README.md + README.es.md DONE.)
- CI workflows (build/test/scan only; no publishing).
- v0.2 (not started): Tika extraction, evidence/chain-of-custody, knowledge base, tasks.

## Known bugs
- None open. Fixed this session (edge/prod): **PDF viewer blank in the composed stack** — nginx's bundled `mime.types` has no `.mjs` entry, so `/pdfjs/pdf.worker.min.mjs` was served as `application/octet-stream` and the browser refused the ES-module worker under `nosniff` ("Setting up fake worker failed"). Fixed with a `location ~* \.mjs$` block that sets `default_type application/javascript` (`infrastructure/nginx/nginx.conf`); verified the worker now serves `application/javascript` and the PDF renders. Only surfaced through nginx, not in `ng serve`. Fixed this session (frontend): report-studio editor not mounting (zoneless timing → `effect` gate); inserting a finding replaced the previous block (`insertContentAt(selection.to)`); finding blocks showed raw UUID after reload (wait for findings before creating editor); PDF.js viewer blank (`globalThis.pdfjsLib` set before importing the viewer); **production build unstyled** — Angular's critical-CSS inliner (beasties) broke Tailwind 4 `@layer` ordering; fixed by `optimization.styles.inlineCritical: false` in `angular.json` (only surfaced in the composed prod build, not `ng serve`). Fixed (backend/renderer): plain-text newlines lost in PDF (`\n`→`<br>`); finding heading could be orphaned at a page break (`.finding-head` break-inside:avoid). Polish: dashboard activity codes made readable; saving a finding returns to the Findings tab.

## Technical debt
- v0.2 modules (evidence, knowledge, tasks) are hidden from the UI until implemented.

## Security limitations
- Threat model T-01…T-20 implemented in the backend; external verification of XSS/CSP under the full composed stack and an authenticated session is pending. No penetration test of ST Atlas itself yet (the Sellax assessment used ST Atlas as the tool, not the target).

## Blockers
- Docker buildx not installed on the dev host (BuildKit cache mounts). Workaround in use; not a product blocker.

## Tests executed
- Backend: 33 unit + 12 integration (Testcontainers Postgres + Gotenberg) — pass. ModularityTests — pass.
- Frontend: `ng build` (dev + prod config) — clean. Manual browser verification of every routed page.

## Decisions requiring approval (ST DevSec)
- Final license (ADR 0010) — no LICENSE committed yet.
- Official logo/branding assets (text wordmark used meanwhile).
- Public repository location (`github.com/STDevSec/st-atlas` proposed) and publishing of images to GHCR.

## Next steps
- Verify the composed stack on `http://localhost:8080`; demo-seed + backup/restore scripts; READMEs; Playwright journey + screenshots; CI.
