package com.stdevsec.atlas.workspace.internal;

import com.stdevsec.atlas.workspace.WorkspaceRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMember.Key> {

    @Query("select m.role from WorkspaceMember m where m.workspaceId = :workspaceId and m.userId = :userId")
    Optional<WorkspaceRole> findRole(UUID workspaceId, UUID userId);

    @Query("select m.workspaceId from WorkspaceMember m where m.userId = :userId")
    List<UUID> findWorkspaceIds(UUID userId);

    List<WorkspaceMember> findByUserId(UUID userId);

    List<WorkspaceMember> findByWorkspaceId(UUID workspaceId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);
}
