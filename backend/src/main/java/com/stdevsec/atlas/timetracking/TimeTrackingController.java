package com.stdevsec.atlas.timetracking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/time-entries")
class TimeTrackingController {

    private final TimeTrackingService service;

    TimeTrackingController(TimeTrackingService service) {
        this.service = service;
    }

    record Manual(@NotNull UUID assessmentId, @NotNull Instant startedAt, @NotNull Instant endedAt, boolean billable,
            @Size(max = 2000) String description) {}

    record Start(@NotNull UUID assessmentId, boolean billable, @Size(max = 2000) String description) {}

    @GetMapping
    List<TimeEntryView> list(@PathVariable UUID workspaceId, @RequestParam(required = false) UUID assessmentId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return service.list(workspaceId, assessmentId, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TimeEntryView create(@PathVariable UUID workspaceId, @Valid @RequestBody Manual body) {
        return service.addManual(workspaceId, new TimeTrackingService.ManualEntry(body.assessmentId(),
                body.startedAt(), body.endedAt(), body.billable(), body.description()));
    }

    @GetMapping("/timer")
    ResponseEntity<TimeEntryView> current(@PathVariable UUID workspaceId) {
        TimeEntryView running = service.current(workspaceId);
        return running == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(running);
    }

    @PostMapping("/timer/start")
    @ResponseStatus(HttpStatus.CREATED)
    TimeEntryView start(@PathVariable UUID workspaceId, @Valid @RequestBody Start body) {
        return service.start(workspaceId, body.assessmentId(), body.billable(), body.description());
    }

    @PostMapping("/timer/stop")
    TimeEntryView stop(@PathVariable UUID workspaceId) {
        return service.stop(workspaceId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        service.delete(workspaceId, id);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    ResponseEntity<String> csv(@PathVariable UUID workspaceId, @RequestParam(required = false) UUID assessmentId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"timesheet.csv\"")
                .body(service.csv(workspaceId, assessmentId, from, to));
    }
}
