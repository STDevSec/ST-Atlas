# 0008 — Versions and toolchain

Selected on 2026-11-24 as the latest stable releases that work together:
Java 21 LTS (Temurin 21.0.13), Spring Boot 4.2.0, Spring Modulith 2.2.0,
Maven wrapper; Angular 22.3, TypeScript 6.0, Tailwind CSS 4.3, Tiptap 3.35,
pdfjs-dist 6.5, Playwright 1.65, Vitest 5; PostgreSQL 18, Gotenberg 9,
Apache Tika server (pinned tag in compose.yaml).

The build host had Java 27 as default; Spring Boot 4.2 targets Java 17–25, so
JDK 21 is installed per user (`~/.local/share/jdks/temurin-21`) and all
containers build with JDK 21. Exact versions are pinned in `pom.xml`,
`package-lock.json` and `compose.yaml`.
