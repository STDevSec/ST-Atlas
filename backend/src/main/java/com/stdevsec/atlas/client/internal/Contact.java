package com.stdevsec.atlas.client.internal;

import com.stdevsec.atlas.client.ClientView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "contact")
public class Contact {

    @Id
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    private String name;
    private String email;
    private String phone;
    private String kind;

    protected Contact() {}

    public Contact(UUID id, UUID workspaceId, String name, String email, String phone, String kind) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.kind = kind;
    }

    ClientView.ContactView toView() {
        return new ClientView.ContactView(id, name, email, phone, kind);
    }
}
