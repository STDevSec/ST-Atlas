# API design

- Base path `/api/v1`, JSON, UTF-8. OpenAPI document at `/api/v1/openapi.json`
  and Swagger UI at `/api/v1/docs` — **disabled by default**, enabled with
  `ATLAS_API_DOCS_ENABLED=true` and then only for authenticated administrators.
- Authentication: session cookie `ATLAS_SESSION` (HttpOnly, SameSite=Strict,
  Secure when `ATLAS_SECURE_COOKIES=true`). CSRF: cookie `XSRF-TOKEN` +
  header `X-XSRF-TOKEN` on every state-changing request.
- Workspace resources: `/api/v1/workspaces/{workspaceId}/<collection>[/{id}]`.
- Pagination: `?page=0&size=25&sort=field,asc` → `{ items, page, size, total }`.
  `size` ≤ 100.
- Errors: RFC 9457 `application/problem+json` with `type`, `title`, `status`,
  `detail` (safe text) and, for validation, `errors: [{field, message}]`.
  Never stack traces, SQL or internal paths.
- Status codes: 200/201/204; 400 validation; 401 unauthenticated; 403 forbidden;
  **404 also for resources in workspaces you cannot access** (no existence oracle);
  409 conflict (lifecycle, versions); 413 payload too large; 415 unsupported
  media; 429 rate limited.

## Endpoints (v0.1)

| Method & path | Purpose |
|---|---|
| `GET /setup/status` | whether first-run setup is pending (no details) |
| `POST /setup` | complete setup (requires setup token) |
| `POST /auth/login` | username + password (+ `totp` or `recoveryCode` when MFA enabled) |
| `POST /auth/logout` | invalidate session |
| `GET /auth/me` | current user, workspaces and roles |
| `POST /auth/mfa/setup` · `POST /auth/mfa/enable` · `POST /auth/mfa/disable` | TOTP enrolment (returns otpauth URI + recovery codes once) |
| `GET/POST /workspaces` · `GET/PUT /workspaces/{w}` | workspaces |
| `GET/POST /workspaces/{w}/members` · `PUT/DELETE .../{userId}` | membership |
| `GET/POST /workspaces/{w}/clients` · `GET/PUT/DELETE .../{id}` | clients (+ `/contacts`) |
| `GET /assessment-types` | configured types |
| `GET/POST /workspaces/{w}/assessments` · `GET/PUT .../{id}` · `POST .../{id}/transition` | assessments + lifecycle |
| `GET/POST /workspaces/{w}/assessments/{a}/assets` | assets |
| `GET/POST /workspaces/{w}/assessments/{a}/findings` · `GET/PUT/DELETE /workspaces/{w}/findings/{id}` | findings |
| `POST /workspaces/{w}/attachments` · `GET .../{id}/content` | upload (multipart) / authorized download |
| `GET/POST /workspaces/{w}/assessments/{a}/reports` · `GET/PUT /workspaces/{w}/reports/{id}` | report documents (autosave = PUT with `expectedVersion`) |
| `POST /workspaces/{w}/reports/{id}/render` | render PDF → new report version |
| `GET /workspaces/{w}/reports/{id}/versions` · `GET .../versions/{n}/pdf` | versions + PDF stream |
| `GET/POST/PUT /workspaces/{w}/templates` | templates |
| `GET /workspaces/{w}/search?q=&tags=&tagMode=AND|OR&type=&from=&to=&page=` | global search |
| `GET/POST /workspaces/{w}/time-entries` · `POST .../timer/start|stop` · `GET .../time-entries.csv` | time tracking |
| `GET /workspaces/{w}/dashboard?from=&to=` | dashboard statistics |
| `GET /workspaces/{w}/audit` | audit log (manager/admin) |
