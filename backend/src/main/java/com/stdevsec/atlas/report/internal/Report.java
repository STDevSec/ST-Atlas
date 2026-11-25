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
@Table(name = "report")
public class Report {

    @Id
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "assessment_id")
    private UUID assessmentId;
    private String title;
    @Column(name = "template_id")
    private UUID templateId;
    @JdbcTypeCode(SqlTypes.JSON)
    private String document;
    private int revision;
    private String classification;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected Report() {}

    public Report(UUID id, UUID workspaceId, UUID assessmentId, String title, UUID templateId, String document,
            String classification, UUID createdBy) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.assessmentId = assessmentId;
        this.title = title;
        this.templateId = templateId;
        this.document = document;
        this.classification = classification;
        this.createdBy = createdBy;
    }

    public void save(String title, String document, UUID templateId, String classification) {
        this.title = title;
        this.document = document;
        this.templateId = templateId;
        this.classification = classification;
        this.revision++;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getAssessmentId() { return assessmentId; }
    public String getTitle() { return title; }
    public UUID getTemplateId() { return templateId; }
    public String getDocument() { return document; }
    public int getRevision() { return revision; }
    public String getClassification() { return classification; }
    public Instant getUpdatedAt() { return updatedAt; }
}
