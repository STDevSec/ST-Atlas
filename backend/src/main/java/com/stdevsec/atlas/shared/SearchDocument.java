package com.stdevsec.atlas.shared;

import java.util.List;
import java.util.UUID;

/**
 * What a module wants the search index to know about one of its records.
 * Published inside the owning transaction; the search module stores it.
 */
public record SearchDocument(
        UUID workspaceId,
        String entityType,
        UUID entityId,
        UUID assessmentId,
        String title,
        String body,
        List<String> tags,
        String severity,
        String status,
        String typeCode,
        UUID clientId) {

    /** Event: index (insert or replace) this document. */
    public record Indexed(SearchDocument document) {}

    /** Event: remove a record from the index. */
    public record Removed(String entityType, UUID entityId) {}
}
