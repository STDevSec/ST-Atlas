package com.stdevsec.atlas.audit;

import com.stdevsec.atlas.audit.internal.AuditStore;
import com.stdevsec.atlas.shared.PageResponse;
import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuditController {

    private final AuditStore store;
    private final WorkspaceAccess access;

    AuditController(AuditStore store, WorkspaceAccess access) {
        this.store = store;
        this.access = access;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/audit")
    PageResponse<AuditStore.AuditView> list(@PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        access.require(workspaceId, Permission.MANAGE);
        return store.page(workspaceId, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }
}
