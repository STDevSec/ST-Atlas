package com.stdevsec.atlas.finding.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.stdevsec.atlas.finding.RemediationStatus;
import com.stdevsec.atlas.finding.Severity;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    Optional<Finding> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Finding> findByWorkspaceIdAndAssessmentIdOrderBySeverityRankAscCodeAsc(UUID workspaceId, UUID assessmentId);

    boolean existsByAssessmentIdAndCode(UUID assessmentId, String code);

    long countByAssessmentId(UUID assessmentId);

    @Query("""
            select f from Finding f where f.workspaceId = :ws
              and (:severity is null or f.severity = :severity)
              and (:status is null or f.status = :status)
              and (:q is null or lower(f.title) like :q or lower(f.code) like :q)
            order by f.severityRank, f.updatedAt desc""")
    Page<Finding> search(UUID ws, Severity severity, RemediationStatus status, String q, Pageable pageable);
}
