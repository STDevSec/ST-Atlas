# 0007 — Runtime i18n service

**Decision.** A small signal-based translation service loads `en.json` /
`es.json` and switches language at runtime; the choice is stored in the user
profile. Angular's built-in i18n compiles one bundle per locale, which would
double the image and require a reload to switch.

**Consequences.** Translation keys must be kept in sync (a unit test checks
both files have the same keys).
