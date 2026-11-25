package com.stdevsec.atlas.finding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FindingController {

    private final FindingService service;

    FindingController(FindingService service) {
        this.service = service;
    }

    record FindingRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 50000) String description,
            @Size(max = 100000) String technicalDetails,
            Severity severity,
            @Size(max = 200) String cvss31Vector,
            @Size(max = 300) String cvss40Vector,
            @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal cvss40Score,
            @Size(max = 20) List<@NotBlank @Size(max = 20) String> cwe,
            @Size(max = 50) List<@NotBlank @Size(max = 20) String> cve,
            @Size(max = 20000) String impact,
            @Size(max = 20000) String recommendation,
            @Size(max = 50) List<@NotBlank @Size(max = 500) String> references,
            @Size(max = 30) List<@NotBlank @Size(max = 50) @Pattern(regexp = "[\\p{L}\\p{N}._#+-]+") String> tags,
            RemediationStatus status,
            LocalDate discoveredOn) {

        FindingService.FindingInput toInput() {
            return new FindingService.FindingInput(title, description, technicalDetails, severity, cvss31Vector,
                    cvss40Vector, cvss40Score, nn(cwe), nn(cve), impact, recommendation, nn(references), nn(tags),
                    status == null ? RemediationStatus.OPEN : status, discoveredOn);
        }

        private static List<String> nn(List<String> l) {
            return l == null ? List.of() : l;
        }
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/assessments/{assessmentId}/findings")
    List<FindingView> list(@PathVariable UUID workspaceId, @PathVariable UUID assessmentId) {
        return service.listForAssessment(workspaceId, assessmentId);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/assessments/{assessmentId}/findings")
    @ResponseStatus(HttpStatus.CREATED)
    FindingView create(@PathVariable UUID workspaceId, @PathVariable UUID assessmentId,
            @Valid @RequestBody FindingRequest body) {
        return service.create(workspaceId, assessmentId, body.toInput());
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/findings")
    com.stdevsec.atlas.shared.PageResponse<FindingService.FindingListItem> search(@PathVariable UUID workspaceId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Severity severity,
            @org.springframework.web.bind.annotation.RequestParam(required = false) RemediationStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "25") int size) {
        return service.search(workspaceId, severity, status, q, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/findings/{id}")
    FindingView get(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        return service.get(workspaceId, id);
    }

    @PutMapping("/api/v1/workspaces/{workspaceId}/findings/{id}")
    FindingView update(@PathVariable UUID workspaceId, @PathVariable UUID id, @Valid @RequestBody FindingRequest body) {
        return service.update(workspaceId, id, body.toInput());
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/findings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        service.delete(workspaceId, id);
    }
}
