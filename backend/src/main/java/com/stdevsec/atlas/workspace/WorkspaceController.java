package com.stdevsec.atlas.workspace;

import com.stdevsec.atlas.identity.CurrentUser;
import com.stdevsec.atlas.identity.UserDirectory;
import com.stdevsec.atlas.identity.UserSummary;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WorkspaceController {

    private final WorkspaceService service;
    private final UserDirectory users;

    WorkspaceController(WorkspaceService service, UserDirectory users) {
        this.service = service;
        this.users = users;
    }

    record Me(UserSummary user, List<WorkspaceService.WorkspaceView> workspaces) {}

    @GetMapping("/api/v1/auth/me")
    Me me() {
        return new Me(users.get(CurrentUser.id()), service.mine());
    }

    record NameRequest(@NotBlank @Size(max = 120) String name) {}

    @GetMapping("/api/v1/workspaces")
    List<WorkspaceService.WorkspaceView> list() {
        return service.mine();
    }

    @PostMapping("/api/v1/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    WorkspaceService.WorkspaceView create(@Valid @RequestBody NameRequest body) {
        return service.create(body.name(), CurrentUser.id());
    }

    @PutMapping("/api/v1/workspaces/{workspaceId}")
    WorkspaceService.WorkspaceView rename(@PathVariable UUID workspaceId, @Valid @RequestBody NameRequest body) {
        return service.rename(workspaceId, body.name());
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/members")
    List<WorkspaceService.MemberView> members(@PathVariable UUID workspaceId) {
        return service.listMembers(workspaceId);
    }

    record AddMember(@NotBlank @Size(max = 64) String username, @NotNull WorkspaceRole role) {}

    @PostMapping("/api/v1/workspaces/{workspaceId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    WorkspaceService.MemberView addMember(@PathVariable UUID workspaceId, @Valid @RequestBody AddMember body) {
        return service.addMember(workspaceId, body.username(), body.role());
    }

    record RoleChange(@NotNull WorkspaceRole role) {}

    @PutMapping("/api/v1/workspaces/{workspaceId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeRole(@PathVariable UUID workspaceId, @PathVariable UUID userId, @Valid @RequestBody RoleChange body) {
        service.changeRole(workspaceId, userId, body.role());
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID workspaceId, @PathVariable UUID userId) {
        service.removeMember(workspaceId, userId);
    }
}
