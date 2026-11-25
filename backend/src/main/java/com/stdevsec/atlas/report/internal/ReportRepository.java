package com.stdevsec.atlas.report.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Report> findByWorkspaceIdAndAssessmentIdOrderByUpdatedAtDesc(UUID workspaceId, UUID assessmentId);

    Page<Report> findByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId, Pageable pageable);
}
