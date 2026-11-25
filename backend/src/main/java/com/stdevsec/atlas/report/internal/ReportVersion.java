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
@Table(name = "report_version")
public class ReportVersion {

    @Id
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "report_id")
    private UUID reportId;
    private int number;
    @JdbcTypeCode(SqlTypes.JSON)
    private String document;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_config")
    private String templateConfig;
    @Column(name = "pdf_attachment_id")
    private UUID pdfAttachmentId;
    @Column(name = "page_count")
    private Integer pageCount;
    private boolean finalized;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected ReportVersion() {}

    public ReportVersion(UUID id, UUID workspaceId, UUID reportId, int number, String document, String templateConfig,
            UUID pdfAttachmentId, Integer pageCount, boolean finalized, UUID createdBy) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.reportId = reportId;
        this.number = number;
        this.document = document;
        this.templateConfig = templateConfig;
        this.pdfAttachmentId = pdfAttachmentId;
        this.pageCount = pageCount;
        this.finalized = finalized;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public int getNumber() { return number; }
    public UUID getPdfAttachmentId() { return pdfAttachmentId; }
    public Integer getPageCount() { return pageCount; }
    public boolean isFinalized() { return finalized; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
