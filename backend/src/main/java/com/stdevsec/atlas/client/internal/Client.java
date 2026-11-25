package com.stdevsec.atlas.client.internal;

import com.stdevsec.atlas.client.ClientView;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "client")
public class Client {

    @Id
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    private String name;
    @Column(name = "legal_name")
    private String legalName;
    private String website;
    private String notes;
    @Column(name = "logo_attachment_id")
    private UUID logoAttachmentId;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = false)
    @OrderBy("name")
    private List<Contact> contacts = new ArrayList<>();

    protected Client() {}

    public Client(UUID id, UUID workspaceId) {
        this.id = id;
        this.workspaceId = workspaceId;
    }

    public void update(String name, String legalName, String website, String notes, UUID logoAttachmentId) {
        this.name = name;
        this.legalName = legalName;
        this.website = website;
        this.notes = notes;
        this.logoAttachmentId = logoAttachmentId;
        this.updatedAt = Instant.now();
    }

    public void replaceContacts(List<Contact> newContacts) {
        contacts.clear();
        contacts.addAll(newContacts);
    }

    public ClientView toView() {
        return new ClientView(id, name, legalName, website, notes, logoAttachmentId,
                contacts.stream().map(Contact::toView).toList(), updatedAt);
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public String getLegalName() { return legalName; }
    public String getWebsite() { return website; }
    public String getNotes() { return notes; }
}
