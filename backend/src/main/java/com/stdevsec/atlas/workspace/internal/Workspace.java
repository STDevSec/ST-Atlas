package com.stdevsec.atlas.workspace.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace")
public class Workspace {

    @Id
    private UUID id;
    private String name;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected Workspace() {}

    public Workspace(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void rename(String name) { this.name = name; }
}
