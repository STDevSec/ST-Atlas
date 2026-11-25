package com.stdevsec.atlas.report.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "report_template")
public class ReportTemplate {

    @Id
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    private String name;
    @JdbcTypeCode(SqlTypes.JSON)
    private String config;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected ReportTemplate() {}

    public ReportTemplate(UUID id, UUID workspaceId, String name, String config) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.config = config;
    }

    public void update(String name, String config) {
        this.name = name;
        this.config = config;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public String getConfig() { return config; }
    public Instant getUpdatedAt() { return updatedAt; }
}
