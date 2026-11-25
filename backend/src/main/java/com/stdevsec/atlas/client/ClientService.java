package com.stdevsec.atlas.client;

import com.stdevsec.atlas.client.internal.Client;
import com.stdevsec.atlas.client.internal.ClientRepository;
import com.stdevsec.atlas.client.internal.Contact;
import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.shared.PageResponse;
import com.stdevsec.atlas.shared.SearchDocument;
import com.stdevsec.atlas.storage.AttachmentService;
import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceContext;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

    public record ContactInput(String name, String email, String phone, String kind) {}

    public record ClientInput(String name, String legalName, String website, String notes, UUID logoAttachmentId,
            List<ContactInput> contacts) {}

    private final ClientRepository clients;
    private final WorkspaceAccess access;
    private final AttachmentService attachments;
    private final ApplicationEventPublisher events;

    ClientService(ClientRepository clients, WorkspaceAccess access, AttachmentService attachments,
            ApplicationEventPublisher events) {
        this.clients = clients;
        this.access = access;
        this.attachments = attachments;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientView> list(UUID workspaceId, String query, int page, int size) {
        access.require(workspaceId, Permission.READ);
        PageRequest pr = PageRequest.of(page, size, Sort.by("name").ascending());
        var result = (query == null || query.isBlank()) ? clients.findByWorkspaceId(workspaceId, pr)
                : clients.findByWorkspaceIdAndNameContainingIgnoreCase(workspaceId, query.trim(), pr);
        return PageResponse.of(result, Client::toView);
    }

    @Transactional(readOnly = true)
    public ClientView get(UUID workspaceId, UUID id) {
        access.require(workspaceId, Permission.READ);
        return load(workspaceId, id).toView();
    }

    /** For other modules: the client's display name, if it exists in this workspace. */
    @Transactional(readOnly = true)
    public Optional<ClientView> find(UUID workspaceId, UUID id) {
        access.require(workspaceId, Permission.READ);
        return clients.findByIdAndWorkspaceId(id, workspaceId).map(Client::toView);
    }

    @Transactional
    public ClientView create(UUID workspaceId, ClientInput in) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        checkLogo(workspaceId, in.logoAttachmentId());
        Client c = new Client(Ids.newId(), workspaceId);
        apply(c, in);
        clients.save(c);
        publish(ctx, c, "CLIENT_CREATED");
        return c.toView();
    }

    @Transactional
    public ClientView update(UUID workspaceId, UUID id, ClientInput in) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        checkLogo(workspaceId, in.logoAttachmentId());
        Client c = load(workspaceId, id);
        apply(c, in);
        publish(ctx, c, "CLIENT_UPDATED");
        return c.toView();
    }

    @Transactional
    public void delete(UUID workspaceId, UUID id) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.DELETE);
        Client c = load(workspaceId, id);
        clients.delete(c);
        clients.flush(); // surfaces FK violations (client still referenced by assessments) here
        events.publishEvent(new SearchDocument.Removed("client", id));
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "CLIENT_DELETED", "client", id));
    }

    private void checkLogo(UUID workspaceId, UUID logoId) {
        if (logoId != null) {
            attachments.info(workspaceId, logoId); // must exist in the same workspace
        }
    }

    private Client load(UUID workspaceId, UUID id) {
        return clients.findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> new NotFoundException("Client"));
    }

    private static void apply(Client c, ClientInput in) {
        c.update(in.name().trim(), blankToNull(in.legalName()), blankToNull(in.website()), blankToNull(in.notes()),
                in.logoAttachmentId());
        c.replaceContacts(in.contacts() == null ? List.of() : in.contacts().stream()
                .map(ci -> new Contact(Ids.newId(), c.getWorkspaceId(), ci.name().trim(), blankToNull(ci.email()),
                        blankToNull(ci.phone()), ci.kind()))
                .toList());
    }

    private void publish(WorkspaceContext ctx, Client c, String action) {
        String body = String.join("\n", nz(c.getLegalName()), nz(c.getWebsite()), nz(c.getNotes()));
        events.publishEvent(new SearchDocument.Indexed(new SearchDocument(ctx.workspaceId(), "client", c.getId(), null,
                c.getName(), body, List.of(), null, null, null, c.getId())));
        events.publishEvent(AuditRecord.of(ctx.workspaceId(), ctx.userId(), action, "client", c.getId()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
