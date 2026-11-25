package com.stdevsec.atlas.client;

import com.stdevsec.atlas.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/clients")
class ClientController {

    private final ClientService service;

    ClientController(ClientService service) {
        this.service = service;
    }

    record ContactRequest(@NotBlank @Size(max = 200) String name, @Email @Size(max = 254) String email,
            @Size(max = 50) String phone, @NotBlank @Pattern(regexp = "TECHNICAL|ADMINISTRATIVE|OTHER") String kind) {}

    record ClientRequest(@NotBlank @Size(max = 200) String name, @Size(max = 300) String legalName,
            @Size(max = 300) @Pattern(regexp = "^$|https?://\\S+") String website, @Size(max = 20000) String notes,
            UUID logoAttachmentId, @Size(max = 50) List<@Valid ContactRequest> contacts) {

        ClientService.ClientInput toInput() {
            return new ClientService.ClientInput(name, legalName, website, notes, logoAttachmentId,
                    contacts == null ? List.of() : contacts.stream()
                            .map(c -> new ClientService.ContactInput(c.name(), c.email(), c.phone(), c.kind())).toList());
        }
    }

    @GetMapping
    PageResponse<ClientView> list(@PathVariable UUID workspaceId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return service.list(workspaceId, q, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/{id}")
    ClientView get(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        return service.get(workspaceId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClientView create(@PathVariable UUID workspaceId, @Valid @RequestBody ClientRequest body) {
        return service.create(workspaceId, body.toInput());
    }

    @PutMapping("/{id}")
    ClientView update(@PathVariable UUID workspaceId, @PathVariable UUID id, @Valid @RequestBody ClientRequest body) {
        return service.update(workspaceId, id, body.toInput());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        service.delete(workspaceId, id);
    }
}
