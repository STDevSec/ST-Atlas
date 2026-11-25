package com.stdevsec.atlas.report.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, UUID> {

    @Query("select t from ReportTemplate t where t.workspaceId is null or t.workspaceId = :workspaceId order by t.name")
    List<ReportTemplate> visibleIn(UUID workspaceId);
}
