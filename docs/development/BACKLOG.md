# Backlog

Order = priority. The first vertical slice (spec §29) cuts across phases:
**Client → Assessment → Finding → Report Editor → PDF → Historical Search.**
Status is tracked in IMPLEMENTATION_STATUS.md, not here.

## Slice 1 — foundation + vertical slice (v0.1)

| ID | Item | Req |
|---|---|---|
| B-001 | Repository, docs skeleton, ADRs, CLAUDE.md | — |
| B-002 | Compose stack: nginx, backend, PostgreSQL, Gotenberg, Tika; networks, health checks, non-root | NFR-01/02 |
| B-003 | Flyway baseline: users, sessions, settings, workspaces, members, audit | FR-01..04 |
| B-004 | Setup wizard with setup token | FR-01 |
| B-005 | Login/logout, Argon2id, session cookie, CSRF, rate limiting | FR-02 |
| B-006 | Workspace access guard + role model + isolation tests | FR-03/04 |
| B-007 | Angular shell: layout, navigation, theme (light/dark), i18n en/es, auth guard | FR-25 |
| B-008 | Clients + contacts CRUD | FR-05 |
| B-009 | Assessment types (20 built-in), assessments CRUD, lifecycle transitions, tags | FR-06..08 |
| B-010 | Findings CRUD with severity, CVSS vectors, CWE/CVE, status | FR-09 |
| B-011 | Attachment storage with content validation (images) | FR-11 |
| B-012 | Report Studio: Tiptap editor, autosave, paste/drop images | FR-10/11 |
| B-013 | Templates: typed config (branding, cover, header/footer, paper, classification) | FR-12 |
| B-014 | Renderer JSON→HTML + Gotenberg PDF + report versions | FR-13 |
| B-015 | PDF.js viewer (zoom, pages, search, download, print) | FR-13 |
| B-016 | Search index + global search UI with tags and filters | FR-15 |
| B-017 | Time entries (manual) + CSV export | FR-19 |
| B-018 | Dashboard from DB aggregates | FR-22 |
| B-019 | Audit log (events) | FR-21 |
| B-020 | MFA (TOTP + recovery codes) | FR-02 |
| B-021 | Playwright journey for the slice; screenshots script | §25, §27 |

## Slice 2 (v0.2)

B-030 evidence manager + chain of custody · B-031 PDF import + Tika extraction ·
B-032 knowledge articles + sanitisation · B-033 tasks + retests ·
B-034 timers (start/pause/resume/stop) + statistics · B-035 assets UI ·
B-036 finding templates · B-037 duplicate assessment structure

## Slice 3 (v0.3)

B-040 visual template designer · B-041 finalised version immutability workflow + review step ·
B-042 advanced search filters (severity, status, client, date) · B-043 dashboard date ranges ·
B-044 member management UI · B-045 session list + revocation UI

## Release (v1.0)

B-050 backup/restore/update/health-check scripts + drills · B-051 demo dataset ·
B-052 CI: build, test, lint, CodeQL, Trivy, gitleaks, CycloneDX SBOM, image build ·
B-053 hardening review against THREAT_MODEL · B-054 full user guide · B-055 release prep
