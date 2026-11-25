# 0001 — Modular monolith with Spring Modulith

**Context.** One small team, self-hosted installs on modest hardware, strong
consistency needs (lifecycle, versions, audit). Microservices would multiply
deployment and failure modes for no scaling benefit.

**Decision.** One Spring Boot application split into Spring Modulith modules
(one top-level package each), communicating through public APIs and
application events. `ModularityTests` verifies boundaries on every build.

**Alternatives.** Microservices (operational cost, distributed transactions);
an unstructured monolith (boundaries erode silently); Maven multi-module
(stronger compile-time walls, more build ceremony — revisit if Modulith proves insufficient).

**Consequences.** Single deployable, single database, ACID transactions.
Discipline is enforced by tests, not by the compiler.
