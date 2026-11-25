package com.stdevsec.atlas.assessment;

import com.stdevsec.atlas.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AssessmentController {

    private final AssessmentService service;

    AssessmentController(AssessmentService service) {
        this.service = service;
    }

    record AssessmentRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 20000) String description,
            @NotBlank @Size(max = 60) String typeCode,
            UUID clientId,
            LocalDate startDate,
            LocalDate dueDate,
            @Size(max = 20000) String objectives,
            @Size(max = 20000) String scope,
            @Size(max = 20000) String outOfScope,
            @Size(max = 20000) String rulesOfEngagement,
            @Size(max = 20000) String methodology,
            @Size(max = 30) List<@NotBlank @Size(max = 50) @Pattern(regexp = "[\\p{L}\\p{N}._#+-]+") String> tags) {

        AssessmentService.AssessmentInput toInput() {
            return new AssessmentService.AssessmentInput(title, description, typeCode, clientId, startDate, dueDate,
                    objectives, scope, outOfScope, rulesOfEngagement, methodology, tags == null ? List.of() : tags);
        }
    }

    @GetMapping("/api/v1/assessment-types")
    List<AssessmentService.TypeView> types() {
        return service.types();
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/assessments")
    PageResponse<AssessmentView> list(@PathVariable UUID workspaceId, @RequestParam(required = false) String q,
            @RequestParam(required = false) AssessmentStatus status, @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID clientId, @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "updatedAt") String sort, @RequestParam(defaultValue = "desc") String dir) {
        return service.list(workspaceId, new AssessmentService.Filter(q, status, type, clientId, tag),
                Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort, "asc".equalsIgnoreCase(dir));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/assessments/{id}")
    AssessmentView get(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        return service.get(workspaceId, id);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    AssessmentView create(@PathVariable UUID workspaceId, @Valid @RequestBody AssessmentRequest body) {
        return service.create(workspaceId, body.toInput());
    }

    @PutMapping("/api/v1/workspaces/{workspaceId}/assessments/{id}")
    AssessmentView update(@PathVariable UUID workspaceId, @PathVariable UUID id,
            @Valid @RequestBody AssessmentRequest body) {
        return service.update(workspaceId, id, body.toInput());
    }

    record Transition(@NotNull AssessmentStatus status) {}

    @PostMapping("/api/v1/workspaces/{workspaceId}/assessments/{id}/transition")
    AssessmentView transition(@PathVariable UUID workspaceId, @PathVariable UUID id,
            @Valid @RequestBody Transition body) {
        return service.transition(workspaceId, id, body.status());
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/assessments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        service.delete(workspaceId, id);
    }
}
