package com.stdevsec.atlas.report;

import java.time.Instant;
import java.util.UUID;

/** API views of reports, versions and templates. */
public final class ReportViews {

    private ReportViews() {}

    public record ReportSummary(UUID id, UUID assessmentId, String title, UUID templateId, int revision,
            String classification, Instant updatedAt, Integer latestVersion) {}

    public record ReportDetail(UUID id, UUID assessmentId, String title, UUID templateId, int revision,
            String classification, String document, Instant updatedAt) {}

    public record VersionView(UUID id, int number, UUID pdfAttachmentId, Integer pageCount, boolean finalized,
            UUID createdBy, Instant createdAt) {}

    public record TemplateView(UUID id, String name, boolean builtIn, TemplateConfig config, Instant updatedAt) {}
}
