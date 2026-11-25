# Backend modules

Package root `com.stdevsec.atlas`. Each direct sub-package is a Spring
Modulith application module. Public API = types in the module's root package;
everything in `internal` is private. Verified by `ModularityTests`.

The spec proposed 20 modules. Some were merged where separating them would
only add indirection (documented here, see ADR 0003):

| Module | Covers (spec names) | Responsibility | Depends on |
|---|---|---|---|
| `shared` | — | value types (ids, paging), error model, clock, i18n keys | — |
| `identity` | Identity, Authentication, Authorization, Settings (user) | users, password hashing, MFA, sessions, setup wizard, roles | shared |
| `workspace` | Workspaces | workspaces, memberships, `WorkspaceAccess` guard | identity, shared |
| `client` | Organizations, Clients, Contacts | organisations, clients, contacts, logos | workspace, storage |
| `assessment` | Assessments, Assets | assessment types, assessments, lifecycle, scope, assets, tags | workspace, client |
| `finding` | Findings | findings, severities, CVSS, finding templates | assessment |
| `storage` | (infrastructure) | file storage, validation, hashing, attachments | workspace |
| `evidence` | Evidence | evidence metadata, chain of custody | assessment, storage |
| `report` | Reports, Templates | report documents, versions, templates, rendering, PDF import | assessment, finding, client, storage |
| `knowledge` | Knowledge Base | articles, sanitisation confirmation | workspace |
| `search` | Search | index maintenance (event listeners), queries | workspace (+ events) |
| `timetracking` | Time Tracking | timers, entries, statistics, CSV | assessment |
| `task` | Tasks | tasks, checklists, retests | assessment, finding |
| `audit` | Audit | audit log (event listener), queries | identity (+ events) |
| `dashboard` | (Dashboard) | read-only aggregations | assessment, finding, timetracking |
| `system` | Settings, Import/Export | instance settings, backup metadata, health | shared |

Cross-module communication: direct calls to another module's public service
for queries; application events for side effects (indexing, auditing).
