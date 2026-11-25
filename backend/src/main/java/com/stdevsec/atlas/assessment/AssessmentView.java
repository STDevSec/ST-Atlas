package com.stdevsec.atlas.assessment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AssessmentView(UUID id, String code, String title, String description, String typeCode,
        boolean usesCvss, UUID clientId, String clientName, AssessmentStatus status, Set<AssessmentStatus> nextStatuses,
        LocalDate startDate, LocalDate dueDate, LocalDate completedDate, String objectives, String scope,
        String outOfScope, String rulesOfEngagement, String methodology, List<String> tags, List<UUID> analystIds,
        Instant createdAt, Instant updatedAt) {}
