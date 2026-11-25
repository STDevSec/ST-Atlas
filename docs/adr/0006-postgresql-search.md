# 0006 — PostgreSQL full-text search + pg_trgm

**Decision.** One `search_entry` table maintained by event listeners, with
English and Spanish `tsvector` columns and a trigram index on titles.
Queries use `websearch_to_tsquery` in both configurations, rank with
`ts_rank_cd`, highlight with `ts_headline`, and are always filtered by the
caller's allowed workspaces first.

**Alternatives.** Elasticsearch/OpenSearch/Meilisearch (another service, a
second copy of confidential data, permission sync problems — rejected by the spec).

**Consequences.** Good enough relevance for thousands of documents; search
permissions are enforced by the same SQL that ranks results.
