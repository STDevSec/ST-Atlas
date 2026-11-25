# 0003 — Consolidate the proposed modules

**Decision.** Identity + Authentication + Authorization + user Settings →
`identity`; Organizations + Clients + Contacts → `client`; Assets inside
`assessment`; Reports + Templates → `report`; Import/Export + instance
Settings → `system`; new `storage` (file handling) and `dashboard`
(read-only aggregations). See `docs/architecture/MODULES.md`.

**Why.** These pairs share the same aggregate or change together; splitting
them would create modules whose only purpose is to call each other.

**Consequences.** Fewer, cohesive modules. A module can still be split later
without changing the public API of the others.
