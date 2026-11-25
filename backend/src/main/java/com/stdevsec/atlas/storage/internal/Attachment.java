package com.stdevsec.atlas.storage.internal;

import com.stdevsec.atlas.storage.AttachmentInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachment")
public class Attachment {

    @Id
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "storage_key")
    private String storageKey;
    @Column(name = "original_name")
    private String originalName;
    @Column(name = "mime_type")
    private String mimeType;
    @Column(name = "size_bytes")
    private long sizeBytes;
    private String sha256;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected Attachment() {}

    public Attachment(UUID id, UUID workspaceId, String storageKey, String originalName, String mimeType,
            long sizeBytes, String sha256, UUID uploadedBy) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
    }

    public AttachmentInfo toInfo() {
        return new AttachmentInfo(id, workspaceId, originalName, mimeType, sizeBytes, sha256, uploadedBy, createdAt);
    }

    public String getStorageKey() { return storageKey; }
    public String getSha256() { return sha256; }
}
