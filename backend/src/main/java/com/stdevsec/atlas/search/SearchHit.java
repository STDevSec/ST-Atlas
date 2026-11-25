package com.stdevsec.atlas.search;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One result. {@code snippet} marks matches with U+2063 INVISIBLE SEPARATOR pairs
 * ({@code ⁣match⁣}) instead of HTML, so the client highlights without rendering markup.
 */
public record SearchHit(String entityType, UUID entityId, UUID assessmentId, String title, String snippet,
        List<String> tags, String severity, String status, String typeCode, Instant updatedAt, double rank) {}
