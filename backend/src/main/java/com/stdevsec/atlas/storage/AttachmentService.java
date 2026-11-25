package com.stdevsec.atlas.storage;

import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.shared.UnsupportedMediaException;
import com.stdevsec.atlas.storage.internal.Attachment;
import com.stdevsec.atlas.storage.internal.AttachmentRepository;
import com.stdevsec.atlas.storage.internal.ContentInspector;
import com.stdevsec.atlas.storage.internal.FileStore;
import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceContext;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API for storing and reading files. All methods enforce workspace access. */
@Service
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final FileStore files;
    private final ContentInspector inspector;
    private final WorkspaceAccess access;
    private final ApplicationEventPublisher events;

    AttachmentService(AttachmentRepository attachments, FileStore files, ContentInspector inspector,
            WorkspaceAccess access, ApplicationEventPublisher events) {
        this.attachments = attachments;
        this.files = files;
        this.inspector = inspector;
        this.access = access;
        this.events = events;
    }

    /** Validates and stores an upload from a user (requires WRITE). */
    @Transactional
    public AttachmentInfo upload(UUID workspaceId, UploadKind kind, String originalName, long declaredSize,
            InputStream content) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        if (declaredSize > kind.maxBytes()) {
            throw new UnsupportedMediaException("File exceeds the maximum size for this upload");
        }
        try {
            byte[] bytes = content.readNBytes((int) kind.maxBytes() + 1);
            return storeValidated(ctx, kind, originalName, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Stores bytes produced by the application itself (e.g. rendered PDFs), still validated. */
    @Transactional
    public AttachmentInfo storeGenerated(UUID workspaceId, UploadKind kind, String name, byte[] bytes) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        return storeValidated(ctx, kind, name, bytes);
    }

    private AttachmentInfo storeValidated(WorkspaceContext ctx, UploadKind kind, String originalName, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > kind.maxBytes()) {
            throw new UnsupportedMediaException("File is empty or exceeds the maximum size");
        }
        String mime = inspector.detectAllowed(bytes, kind);
        UUID id = Ids.newId();
        FileStore.Stored stored = files.write(ctx.workspaceId(), id, bytes);
        Attachment a = attachments.save(new Attachment(id, ctx.workspaceId(), stored.storageKey(),
                safeName(originalName), mime, bytes.length, stored.sha256(), ctx.userId()));
        events.publishEvent(new AuditRecord(ctx.workspaceId(), ctx.userId(), "FILE_UPLOADED", "attachment", id,
                Map.of("mime", mime, "size", bytes.length, "sha256", stored.sha256())));
        return a.toInfo();
    }

    @Transactional(readOnly = true)
    public AttachmentInfo info(UUID workspaceId, UUID attachmentId) {
        access.require(workspaceId, Permission.READ);
        return attachments.findByIdAndWorkspaceId(attachmentId, workspaceId).map(Attachment::toInfo)
                .orElseThrow(() -> new NotFoundException("File"));
    }

    /** Opens content after authorization and verifies the stored hash (T-15). */
    @Transactional(readOnly = true)
    public StoredContent open(UUID workspaceId, UUID attachmentId) {
        access.require(workspaceId, Permission.READ);
        Attachment a = attachments.findByIdAndWorkspaceId(attachmentId, workspaceId)
                .orElseThrow(() -> new NotFoundException("File"));
        return new StoredContent(a.toInfo(), files.openVerified(a.getStorageKey(), a.getSha256()));
    }

    /** Reads the bytes (for embedding in renders). Same authorization as {@link #open}. */
    @Transactional(readOnly = true)
    public byte[] readAll(UUID workspaceId, UUID attachmentId) {
        try (InputStream in = open(workspaceId, attachmentId).stream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Keeps only a readable, harmless display name (the real file name is generated). */
    static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}\"<>|*?]", "_").strip();
        return base.isEmpty() ? "file" : base.substring(0, Math.min(base.length(), 255));
    }
}
