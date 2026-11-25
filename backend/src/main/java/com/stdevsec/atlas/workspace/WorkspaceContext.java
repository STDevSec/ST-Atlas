package com.stdevsec.atlas.workspace;

import java.util.UUID;

/** Proof that the current user may act in a workspace, with their effective role. */
public record WorkspaceContext(UUID workspaceId, UUID userId, WorkspaceRole role) {}
