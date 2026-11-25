package com.stdevsec.atlas.workspace;

import com.stdevsec.atlas.identity.CurrentUser;
import com.stdevsec.atlas.identity.UserDirectory;
import com.stdevsec.atlas.identity.UserSummary;
import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.ConflictException;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import com.stdevsec.atlas.workspace.internal.Workspace;
import com.stdevsec.atlas.workspace.internal.WorkspaceMember;
import com.stdevsec.atlas.workspace.internal.WorkspaceMemberRepository;
import com.stdevsec.atlas.workspace.internal.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    public record WorkspaceView(UUID id, String name, WorkspaceRole role) {}

    public record MemberView(UUID userId, String username, String displayName, WorkspaceRole role) {}

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final WorkspaceAccess access;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;

    WorkspaceService(WorkspaceRepository workspaces, WorkspaceMemberRepository members, WorkspaceAccess access,
            UserDirectory users, ApplicationEventPublisher events) {
        this.workspaces = workspaces;
        this.members = members;
        this.access = access;
        this.users = users;
        this.events = events;
    }

    /** Creates a workspace owned (MANAGER) by the given user. */
    @Transactional
    public WorkspaceView create(String name, UUID ownerId) {
        Workspace ws = workspaces.save(new Workspace(Ids.newId(), name.trim()));
        members.save(new WorkspaceMember(ws.getId(), ownerId, WorkspaceRole.MANAGER));
        events.publishEvent(AuditRecord.of(ws.getId(), ownerId, "WORKSPACE_CREATED", "workspace", ws.getId()));
        return new WorkspaceView(ws.getId(), ws.getName(), WorkspaceRole.MANAGER);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceView> mine() {
        UUID me = CurrentUser.id();
        return members.findByUserId(me).stream()
                .map(m -> new WorkspaceView(m.getWorkspaceId(),
                        workspaces.findById(m.getWorkspaceId()).map(Workspace::getName).orElse("?"), m.getRole()))
                .sorted(java.util.Comparator.comparing(WorkspaceView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public WorkspaceView rename(UUID workspaceId, String name) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.MANAGE);
        Workspace ws = workspaces.findById(workspaceId).orElseThrow(() -> new NotFoundException("Workspace"));
        ws.rename(name.trim());
        return new WorkspaceView(ws.getId(), ws.getName(), ctx.role());
    }

    @Transactional(readOnly = true)
    public List<MemberView> listMembers(UUID workspaceId) {
        access.require(workspaceId, Permission.READ);
        List<WorkspaceMember> list = members.findByWorkspaceId(workspaceId);
        Map<UUID, UserSummary> byId = users.findAll(list.stream().map(WorkspaceMember::getUserId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(UserSummary::id, u -> u));
        return list.stream().map(m -> {
            UserSummary u = byId.get(m.getUserId());
            return new MemberView(m.getUserId(), u.username(), u.displayName(), m.getRole());
        }).toList();
    }

    @Transactional
    public MemberView addMember(UUID workspaceId, String username, WorkspaceRole role) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.MANAGE);
        UserSummary user = users.findByUsername(username).orElseThrow(() -> new NotFoundException("User"));
        if (members.findRole(workspaceId, user.id()).isPresent()) {
            throw new ConflictException("User is already a member");
        }
        members.save(new WorkspaceMember(workspaceId, user.id(), role));
        events.publishEvent(new AuditRecord(workspaceId, ctx.userId(), "MEMBER_ADDED", "user", user.id(),
                Map.of("role", role.name())));
        return new MemberView(user.id(), user.username(), user.displayName(), role);
    }

    @Transactional
    public void changeRole(UUID workspaceId, UUID userId, WorkspaceRole role) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.MANAGE);
        WorkspaceMember m = members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new NotFoundException("Member"));
        if (m.getRole() == WorkspaceRole.MANAGER && role != WorkspaceRole.MANAGER
                && members.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.MANAGER) <= 1) {
            throw new ConflictException("A workspace needs at least one manager");
        }
        m.setRole(role);
        events.publishEvent(new AuditRecord(workspaceId, ctx.userId(), "MEMBER_ROLE_CHANGED", "user", userId,
                Map.of("role", role.name())));
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID userId) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.MANAGE);
        WorkspaceMember m = members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new NotFoundException("Member"));
        if (m.getRole() == WorkspaceRole.MANAGER
                && members.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.MANAGER) <= 1) {
            throw new ConflictException("A workspace needs at least one manager");
        }
        members.delete(m);
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "MEMBER_REMOVED", "user", userId));
    }
}
