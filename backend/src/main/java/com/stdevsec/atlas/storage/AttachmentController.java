package com.stdevsec.atlas.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
class AttachmentController {

    private final AttachmentService service;

    AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/v1/workspaces/{workspaceId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AttachmentInfo> upload(@PathVariable UUID workspaceId, @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "IMAGE") UploadKind kind) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.upload(workspaceId, kind, file.getOriginalFilename(), file.getSize(), in));
        }
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/attachments/{id}")
    AttachmentInfo info(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        return service.info(workspaceId, id);
    }

    /**
     * Authorized download. Images and PDFs may be shown inline (the viewer and the
     * editor need it); the type is the one we detected, never the client's claim.
     */
    @GetMapping("/api/v1/workspaces/{workspaceId}/attachments/{id}/content")
    ResponseEntity<StreamingResponseBody> content(@PathVariable UUID workspaceId, @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean download) {
        StoredContent content = service.open(workspaceId, id);
        AttachmentInfo info = content.info();
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(info.originalName(), StandardCharsets.UTF_8).build();
        StreamingResponseBody body = out -> {
            try (InputStream in = content.stream()) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.mimeType()))
                .contentLength(info.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox")
                .body(body);
    }
}
