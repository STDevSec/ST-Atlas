package com.stdevsec.atlas.shared;

import java.util.Map;
import java.util.UUID;

/**
 * An auditable action, published as an application event and persisted by the
 * audit module in the same transaction as the change it describes.
 */
public record AuditRecord(
        UUID workspaceId,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> details) {

    public static AuditRecord of(UUID workspaceId, UUID actorId, String action, String entityType,
            UUID entityId) {
        return new AuditRecord(workspaceId, actorId, action, entityType, entityId, Map.of());
    }
}
