package com.stdevsec.atlas.workspace;

import com.stdevsec.atlas.identity.AtlasPrincipal;
import com.stdevsec.atlas.identity.CurrentUser;
import com.stdevsec.atlas.shared.ForbiddenException;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import com.stdevsec.atlas.workspace.internal.WorkspaceMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization gate for workspace-scoped operations (T-05, T-06). Call it first in
 * every service method that takes a workspace id. Non-members get 404 (the
 * workspace "does not exist" for them); members lacking the permission get 403.
 */
@Component
public class WorkspaceAccess {

    private final WorkspaceMemberRepository members;

    WorkspaceAccess(WorkspaceMemberRepository members) {
        this.members = members;
    }

    @Transactional(readOnly = true)
    public WorkspaceContext require(UUID workspaceId, Permission permission) {
        AtlasPrincipal user = CurrentUser.get();
        WorkspaceRole role = members.findRole(workspaceId, user.id())
                .orElseThrow(() -> new NotFoundException("Workspace"));
        if (!role.allows(permission)) {
            throw new ForbiddenException("Missing permission " + permission);
        }
        return new WorkspaceContext(workspaceId, user.id(), role);
    }

    /** Workspaces the current user belongs to (used to scope search). */
    @Transactional(readOnly = true)
    public List<UUID> accessibleWorkspaceIds() {
        return members.findWorkspaceIds(CurrentUser.id());
    }
}
