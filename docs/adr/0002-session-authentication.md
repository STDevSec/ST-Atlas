# 0002 — Server-side sessions (Spring Session JDBC)

**Context.** Browser-only clients, same-origin SPA behind nginx, need for
immediate revocation (logout everywhere, disabled user, role change).

**Decision.** Cookie session (`ATLAS_SESSION`, HttpOnly, SameSite=Strict,
Secure when HTTPS), stored in PostgreSQL through Spring Session JDBC so it
survives restarts and can be revoked by deleting rows. CSRF via
double-submit cookie. Passwords: Argon2id (Spring Security
`Argon2PasswordEncoder`, BouncyCastle). MFA: TOTP (RFC 6238) + hashed
one-time recovery codes.

**Alternatives.** JWT in localStorage (XSS-exposed, hard to revoke — rejected
by the spec); JWT in cookies (same revocation problem, larger cookies).

**Consequences.** Stateful backend (fine for a monolith); one DB lookup per
request (negligible at this scale).
