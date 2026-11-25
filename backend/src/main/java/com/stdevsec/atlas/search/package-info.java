/**
 * Global search over PostgreSQL full-text (English + Spanish) and trigram indexes (ADR 0006).
 * The index is fed by {@link com.stdevsec.atlas.shared.SearchDocument} events.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Search")
package com.stdevsec.atlas.search;
