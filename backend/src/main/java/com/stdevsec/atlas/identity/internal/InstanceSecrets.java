package com.stdevsec.atlas.identity.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Instance encryption key for secrets at rest (MFA seeds). Taken from
 * {@code ATLAS_ENCRYPTION_KEY} (base64, 32 bytes) if set; otherwise generated on
 * first start into {@code <data-dir>/keys/instance.key} with mode 600. The key
 * is part of backups (docs/deployment/BACKUP_RESTORE.md).
 */
@Component
public class InstanceSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    InstanceSecrets(@Value("${atlas.encryption-key:}") String configured,
            @Value("${atlas.data-dir}") Path dataDir) {
        byte[] raw = configured.isBlank() ? loadOrCreate(dataDir.resolve("keys/instance.key"))
                : Base64.getDecoder().decode(configured.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("Encryption key must be 32 bytes (base64-encoded)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    private static byte[] loadOrCreate(Path file) {
        try {
            if (Files.exists(file)) {
                return Base64.getDecoder().decode(Files.readString(file).trim());
            }
            Files.createDirectories(file.getParent());
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            Files.writeString(file, Base64.getEncoder().encodeToString(raw));
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // non-POSIX filesystem (development on other OSes)
            }
            return raw;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read or create the instance key", e);
        }
    }

    /** AES-256-GCM; output = base64(iv || ciphertext+tag). */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
