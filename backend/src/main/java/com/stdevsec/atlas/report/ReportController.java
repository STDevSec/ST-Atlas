package com.stdevsec.atlas.report;

import com.stdevsec.atlas.report.ReportViews.ReportDetail;
import com.stdevsec.atlas.report.ReportViews.ReportSummary;
import com.stdevsec.atlas.report.ReportViews.TemplateView;
import com.stdevsec.atlas.report.ReportViews.VersionView;
import com.stdevsec.atlas.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
class ReportController {

    private final ReportService reports;
    private final TemplateService templates;

    ReportController(ReportService reports, TemplateService templates) {
        this.reports = reports;
        this.templates = templates;
    }

    record CreateReport(@Size(max = 300) String title, UUID templateId) {}

    record SaveReport(@NotBlank @Size(max = 300) String title, @NotBlank String document, @Min(0) int revision,
            UUID templateId, @NotBlank String classification) {}

    record Render(boolean finalized) {}

    record TemplateRequest(@NotBlank @Size(max = 120) String name, @NotNull @Valid TemplateConfig config) {}

    @GetMapping("/reports")
    PageResponse<ReportSummary> recent(@PathVariable UUID workspaceId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return reports.recent(workspaceId, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/assessments/{assessmentId}/reports")
    List<ReportSummary> forAssessment(@PathVariable UUID workspaceId, @PathVariable UUID assessmentId) {
        return reports.listForAssessment(workspaceId, assessmentId);
    }

    @PostMapping("/assessments/{assessmentId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    ReportDetail create(@PathVariable UUID workspaceId, @PathVariable UUID assessmentId,
            @Valid @RequestBody CreateReport body) {
        return reports.create(workspaceId, assessmentId, body.title(), body.templateId());
    }

    @GetMapping("/reports/{id}")
    ReportDetail get(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        return reports.get(workspaceId, id);
    }

    @PutMapping("/reports/{id}")
    ReportDetail save(@PathVariable UUID workspaceId, @PathVariable UUID id, @Valid @RequestBody SaveReport body) {
        return reports.save(workspaceId, id, body.title(), body.document(), body.revision(), body.templateId(),
                body.classification());
    }

    @PostMapping("/reports/{id}/render")
    @ResponseStatus(HttpStatus.CREATED)
    VersionView render(@PathVariable UUID workspaceId, @PathVariable UUID id, @RequestBody(required = false) Render body) {
        return reports.render(workspaceId, id, body != null && body.finalized());
    }

    @GetMapping("/reports/{id}/versions")
    List<VersionView> versions(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        return reports.versions(workspaceId, id);
    }

    @GetMapping("/templates")
    List<TemplateView> templates(@PathVariable UUID workspaceId) {
        return templates.list(workspaceId);
    }

    @GetMapping("/templates/{id}")
    TemplateView template(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        return templates.get(workspaceId, id);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    TemplateView createTemplate(@PathVariable UUID workspaceId, @Valid @RequestBody TemplateRequest body) {
        return templates.create(workspaceId, body.name(), body.config());
    }

    @PutMapping("/templates/{id}")
    TemplateView updateTemplate(@PathVariable UUID workspaceId, @PathVariable UUID id,
            @Valid @RequestBody TemplateRequest body) {
        return templates.update(workspaceId, id, body.name(), body.config());
    }

    @PostMapping("/templates/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    TemplateView duplicate(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        return templates.duplicate(workspaceId, id);
    }

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTemplate(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        templates.delete(workspaceId, id);
    }
}
