# 0005 — Gotenberg and Tika in isolated containers

**Decision.** PDFs are produced by Gotenberg (Chromium) and PDF text is
extracted by Apache Tika server, each in its own container on an
`internal: true` network that has no internet egress and no route to the
database. The backend sends bytes and receives bytes. Gotenberg JavaScript is
disabled and outbound URLs are denied.

**Alternatives.** In-process PDF libraries (OpenPDF/Flying Saucer: weaker
CSS, parsing hostile PDFs in the main JVM); a headless browser inside the
backend image (larger attack surface in the trusted process).

**Consequences.** Two extra containers (~1 GB RAM total, to be measured);
rendering quality of a real browser engine; parser vulnerabilities contained.
