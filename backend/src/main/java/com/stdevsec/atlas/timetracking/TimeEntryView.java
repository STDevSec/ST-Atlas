package com.stdevsec.atlas.timetracking;

import java.time.Instant;
import java.util.UUID;

public record TimeEntryView(UUID id, UUID assessmentId, String assessmentCode, String assessmentTitle, UUID userId,
        Instant startedAt, Instant endedAt, long durationSeconds, boolean running, boolean billable,
        String description) {}
