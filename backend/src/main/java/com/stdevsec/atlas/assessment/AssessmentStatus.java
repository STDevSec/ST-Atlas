package com.stdevsec.atlas.assessment;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Default lifecycle (spec §11) and the transitions the server allows. */
public enum AssessmentStatus {
    DRAFT, PLANNED, IN_PROGRESS, TECHNICAL_REVIEW, APPROVED, DELIVERED, RETESTING, COMPLETED, ARCHIVED;

    private static final Map<AssessmentStatus, Set<AssessmentStatus>> NEXT = Map.of(
            DRAFT, EnumSet.of(PLANNED, IN_PROGRESS, ARCHIVED),
            PLANNED, EnumSet.of(DRAFT, IN_PROGRESS, ARCHIVED),
            IN_PROGRESS, EnumSet.of(PLANNED, TECHNICAL_REVIEW),
            TECHNICAL_REVIEW, EnumSet.of(IN_PROGRESS, APPROVED),
            APPROVED, EnumSet.of(TECHNICAL_REVIEW, DELIVERED),
            DELIVERED, EnumSet.of(RETESTING, COMPLETED),
            RETESTING, EnumSet.of(DELIVERED, COMPLETED),
            COMPLETED, EnumSet.of(RETESTING, ARCHIVED),
            ARCHIVED, EnumSet.of(COMPLETED));

    public boolean canMoveTo(AssessmentStatus target) {
        return NEXT.get(this).contains(target);
    }

    public Set<AssessmentStatus> next() {
        return NEXT.get(this);
    }

    /** Statuses counted as "active" on the dashboard. */
    public static final Set<AssessmentStatus> ACTIVE = EnumSet.of(PLANNED, IN_PROGRESS, RETESTING);
}
