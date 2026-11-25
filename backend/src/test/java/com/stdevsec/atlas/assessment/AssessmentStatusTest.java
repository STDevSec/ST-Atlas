package com.stdevsec.atlas.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssessmentStatusTest {

    @Test
    void followsTheDefaultLifecycle() {
        assertThat(AssessmentStatus.DRAFT.canMoveTo(AssessmentStatus.IN_PROGRESS)).isTrue();
        assertThat(AssessmentStatus.IN_PROGRESS.canMoveTo(AssessmentStatus.TECHNICAL_REVIEW)).isTrue();
        assertThat(AssessmentStatus.TECHNICAL_REVIEW.canMoveTo(AssessmentStatus.APPROVED)).isTrue();
        assertThat(AssessmentStatus.APPROVED.canMoveTo(AssessmentStatus.DELIVERED)).isTrue();
        assertThat(AssessmentStatus.DELIVERED.canMoveTo(AssessmentStatus.COMPLETED)).isTrue();
    }

    @Test
    void forbidsSkippingReview() {
        assertThat(AssessmentStatus.IN_PROGRESS.canMoveTo(AssessmentStatus.DELIVERED)).isFalse();
        assertThat(AssessmentStatus.DRAFT.canMoveTo(AssessmentStatus.COMPLETED)).isFalse();
    }

    @Test
    void everyStatusHasAWayOut() {
        for (AssessmentStatus s : AssessmentStatus.values()) {
            assertThat(s.next()).isNotEmpty();
        }
    }
}
