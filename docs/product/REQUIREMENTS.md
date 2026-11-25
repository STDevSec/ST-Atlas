# Requirements

IDs are stable; reference them in tests, commits and the backlog.
Priority: **M** must (v0.1), **S** should (v0.2–v0.3), **C** could (v1.0+).

## Functional requirements

| ID | Requirement | P |
|---|---|---|
| FR-01 | First-run setup creates the administrator, organisation, language, time zone and first workspace; it is only reachable before setup completes and requires the one-time setup token printed in the backend log | M |
| FR-02 | Username/password login with Argon2id hashes; optional TOTP MFA with recovery codes; logout; session expiry and revocation | M |
| FR-03 | Roles per workspace: Administrator (global), Workspace Manager, Analyst, Reviewer, Viewer | M |
| FR-04 | Workspaces isolate all data: queries, files and search never cross workspaces | M |
| FR-05 | Organisations/clients with legal name, contacts (technical/administrative), notes, logo | M |
| FR-06 | Assessments with type, client (optional), analysts, dates, objectives, scope, out-of-scope, rules of engagement, methodology, tags, lifecycle status | M |
| FR-07 | Configurable assessment types (20 built-in), each declaring whether findings use CVSS and which report sections apply | M |
| FR-08 | Lifecycle Draft → Planned → In Progress → Technical Review → Approved → Delivered → Retesting → Completed → Archived, with allowed transitions enforced server-side | M |
| FR-09 | Findings: code, title, description, technical details, severity, CVSS 3.1/4.0 vector + score, CWE/CVE, affected assets, impact, recommendations, references, tags, remediation status | M |
| FR-10 | Report Studio: rich text (headings, lists, tables, links, quotes, code with highlighting, images with captions, page breaks, separators), undo/redo, autosave, sections | M |
| FR-11 | Pasting or dropping an image uploads it as an attachment and inserts a reference (never permanent Base64) that survives reloads | M |
| FR-12 | Templates: title, cover, logos, header/footer, page numbers, typography, colours, margins, A4/Letter, orientation, classification, watermark, section order, variables `{{...}}` | M (core) / S (visual designer) |
| FR-13 | PDF generation from the structured document through an isolated renderer; in-app PDF.js viewer with zoom, navigation, search, download, print | M |
| FR-14 | Finalised report versions are immutable; edits create a new version | S |
| FR-15 | Global search (titles, descriptions, notes, report text, extracted PDF text, tags) in English and Spanish with partial matching, highlighting, filters and pagination, respecting permissions | M |
| FR-16 | Evidence upload with SHA-256, MIME detection, metadata, classification, links to findings; chain-of-custody events; external references for large images | S |
| FR-17 | PDF import with text extraction; scanned PDFs flagged as "no searchable text" | S |
| FR-18 | Knowledge articles (procedures, checklists, commands, lessons learned) with explicit sanitisation confirmation before sharing client data | S |
| FR-19 | Time tracking: start/pause/resume/stop timer persisted server-side, manual entries, billable flag, estimates, CSV export, statistics | M (entries) / S (timer, stats) |
| FR-20 | Tasks with priority, due date, status, checklist, links to findings; retest workflow | S |
| FR-21 | Audit log of security-relevant and data-changing actions | M |
| FR-22 | Dashboard statistics computed from the database | M |
| FR-23 | Backup and restore of database, files and configuration with integrity verification | S |
| FR-24 | Optional demo dataset, never loaded automatically | S |
| FR-25 | UI in English (default) and Spanish; light/dark theme persisted | M |

## Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-01 | Install with Docker Compose only; app on `http://localhost:8080`, bound to 127.0.0.1 by default |
| NFR-02 | No outbound network calls at runtime; renderer and extractor have no network egress |
| NFR-03 | OWASP ASVS L2 used as reference; OWASP Top 10 mitigations documented in SECURITY_CONTROLS.md |
| NFR-04 | Runs on 4 CPU / 4 GB RAM / 10 GB disk (to be measured) |
| NFR-05 | Data survives container restarts (named volumes) |
| NFR-06 | Accessibility: keyboard navigation, visible focus, WCAG AA contrast |
| NFR-07 | p95 API latency < 300 ms for list endpoints with 10k findings (to be measured) |
| NFR-08 | All dependencies pinned; SBOM generated in CI |
| NFR-09 | Module boundaries verified by tests (Spring Modulith) |
