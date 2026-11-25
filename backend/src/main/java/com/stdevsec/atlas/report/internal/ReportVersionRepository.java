package com.stdevsec.atlas.report.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReportVersionRepository extends JpaRepository<ReportVersion, UUID> {

    List<ReportVersion> findByWorkspaceIdAndReportIdOrderByNumberDesc(UUID workspaceId, UUID reportId);

    @Query("select coalesce(max(v.number), 0) from ReportVersion v where v.reportId = :reportId")
    int maxNumber(UUID reportId);

    Optional<ReportVersion> findByWorkspaceIdAndReportIdAndNumber(UUID workspaceId, UUID reportId, int number);
}
