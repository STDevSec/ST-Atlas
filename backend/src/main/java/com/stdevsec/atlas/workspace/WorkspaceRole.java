package com.stdevsec.atlas.workspace;

import java.util.EnumSet;
import java.util.Set;

/** Roles inside a workspace and the permissions each grants. */
public enum WorkspaceRole {
    MANAGER(EnumSet.allOf(Permission.class)),
    ANALYST(EnumSet.of(Permission.READ, Permission.WRITE, Permission.DELETE)),
    REVIEWER(EnumSet.of(Permission.READ, Permission.WRITE, Permission.REVIEW)),
    VIEWER(EnumSet.of(Permission.READ));

    private final Set<Permission> permissions;

    WorkspaceRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public boolean allows(Permission permission) {
        return permissions.contains(permission);
    }

    public enum Permission { READ, WRITE, DELETE, REVIEW, MANAGE }
}
