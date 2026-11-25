package com.stdevsec.atlas.assessment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What other modules need to know about an assessment. */
public record AssessmentRef(UUID id, UUID workspaceId, String code, String title, String typeCode, boolean usesCvss,
        UUID clientId, AssessmentStatus status, LocalDate startDate, LocalDate dueDate, LocalDate completedDate,
        String objectives, String scope, String methodology, List<String> tags) {}
