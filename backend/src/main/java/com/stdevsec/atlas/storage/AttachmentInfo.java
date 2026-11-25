package com.stdevsec.atlas.storage;

import java.time.Instant;
import java.util.UUID;

/** Metadata of a stored file. */
public record AttachmentInfo(UUID id, UUID workspaceId, String originalName, String mimeType, long sizeBytes,
        String sha256, UUID uploadedBy, Instant createdAt) {}
