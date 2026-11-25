# Product Requirements Document — ST Atlas

Status: living document. Owner: ST DevSec. Last update: 2026-11-24.

## 1. Summary

ST Atlas — *Security Assessment & Reporting Platform* — is self-hosted software
for planning, executing, documenting and delivering technical security work,
and for finding that knowledge again later. It runs entirely on the owner's
infrastructure with `docker compose up`.

Tagline: **Manage assessments. Document evidence. Preserve knowledge.**

## 2. Problem

Security professionals keep assessment knowledge scattered across word
processors, screenshot folders, spreadsheets for hours, and old PDFs that
nobody can search. Existing reporting tools (PwnDoc, SysReptor, WriteHat,
PeTeReport) focus on penetration-test reports; forensic, incident-response,
NOC, hardening or educational work does not fit their vulnerability-centric
model. Hosted SaaS alternatives require trusting a third party with the most
sensitive documents an organisation has.

## 3. Users

| Persona | Needs |
|---|---|
| Independent consultant | one place for clients, assessments, findings, evidence, hours and reports; professional PDFs with their branding |
| Small security team | shared workspace, roles, review before delivery, consistent templates |
| Forensic / IR analyst | evidence with hashes and chain of custody, reports without CVSS |
| Student / researcher | CTF write-ups, lab notes, a searchable personal knowledge base |

## 4. Product principles

Secure by design and by default; privacy first; local first; no mandatory
accounts, subscriptions, telemetry or cloud services; accessible; extensible;
maintainable by a small team.

## 5. Scope

### In scope (v0.x → v1.0)

Workspaces and roles; organisations, clients and contacts; configurable
assessment types and lifecycle; assets; structured findings (severity, CVSS,
CWE/CVE, remediation state); evidence with SHA-256 and chain of custody;
Report Studio (rich text, pasted images, versions); templates with branding;
PDF generation and in-app viewer; PDF import with text extraction; knowledge
articles; global full-text search with tags and filters; time tracking;
tasks and retesting; audit log; backup/restore; demo dataset; English and
Spanish UI.

### Out of scope until the core is stable (see ROADMAP.md)

Scanner imports (Nmap, Nessus, Burp, ZAP, OpenVAS, SARIF), collaborative
real-time editing, client portal, OIDC/LDAP, DOCX export, OCR, digital
signatures, S3 storage, local AI, MCP server, plugins, ST ecosystem integrations.

## 6. Release plan

| Release | Content |
|---|---|
| **v0.1 — vertical slice** | install wizard, login with MFA, workspace, clients, assessments, findings, Report Studio with pasted images, template branding, PDF generation + viewer, global search, time entries, audit log basics |
| v0.2 | evidence manager with chain of custody, PDF import + extraction, knowledge articles, tasks/retesting, timers |
| v0.3 | template designer UI, report version immutability workflow, advanced search filters, dashboards by date range |
| v1.0 | hardening review, backup/restore drills, demo dataset, screenshots, CI with scanning, release candidate |

## 7. Success criteria for v1.0

The critical user journey in spec §25 passes end-to-end with Playwright on a
fresh `docker compose up -d --build`, and survives a container restart and a
backup/restore cycle.

## 8. Non-goals

ST Atlas is not a vulnerability scanner, an exploitation framework, a
certified forensic acquisition tool, or a general project-management suite.
