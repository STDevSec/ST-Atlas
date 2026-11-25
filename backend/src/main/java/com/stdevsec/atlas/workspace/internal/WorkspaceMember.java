package com.stdevsec.atlas.workspace.internal;

import com.stdevsec.atlas.workspace.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "workspace_member")
@IdClass(WorkspaceMember.Key.class)
public class WorkspaceMember {

    public record Key(UUID workspaceId, UUID userId) implements Serializable {
        public Key() { this(null, null); }
    }

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private WorkspaceRole role;

    protected WorkspaceMember() {}

    public WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getUserId() { return userId; }
    public WorkspaceRole getRole() { return role; }
    public void setRole(WorkspaceRole role) { this.role = role; }
}
