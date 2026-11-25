package com.stdevsec.atlas.storage;

import java.util.Set;

/** What an upload is for, which decides the allowed content types and size. */
public enum UploadKind {
    /** Images in reports, logos: no SVG (can carry script), no animated formats beyond GIF. */
    IMAGE(Set.of("image/png", "image/jpeg", "image/gif", "image/webp"), 15L * 1024 * 1024),
    PDF(Set.of("application/pdf"), 50L * 1024 * 1024);

    private final Set<String> allowedTypes;
    private final long maxBytes;

    UploadKind(Set<String> allowedTypes, long maxBytes) {
        this.allowedTypes = allowedTypes;
        this.maxBytes = maxBytes;
    }

    public Set<String> allowedTypes() { return allowedTypes; }
    public long maxBytes() { return maxBytes; }
}
