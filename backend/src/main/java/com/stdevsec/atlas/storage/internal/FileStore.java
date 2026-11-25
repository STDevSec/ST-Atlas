package com.stdevsec.atlas.storage.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local filesystem store. Keys are generated ({@code ws/yyyy/mm/uuid}); user input never
 * becomes part of a path, and every resolved path is checked to stay under the root (T-11).
 */
@Component
public class FileStore {

    public record Stored(String storageKey, String sha256) {}

    private final Path root;

    FileStore(@Value("${atlas.storage-dir}") Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public Stored write(UUID workspaceId, UUID id, byte[] bytes) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String key = "%s/%04d/%02d/%s".formatted(workspaceId, today.getYear(), today.getMonthValue(), id);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            return new Stored(key, sha256(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        }
    }

    /** Opens a file and checks its SHA-256 against the recorded value before returning it. */
    public InputStream openVerified(String storageKey, String expectedSha256) {
        try {
            byte[] bytes = Files.readAllBytes(resolve(storageKey));
            if (!sha256(bytes).equals(expectedSha256)) {
                throw new IllegalStateException("Integrity check failed for stored file " + storageKey);
            }
            return new ByteArrayInputStream(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read stored file", e);
        }
    }

    Path resolve(String storageKey) {
        Path p = root.resolve(storageKey).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return p;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
