package com.stdevsec.atlas.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_code")
public class RecoveryCode {

    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "code_hash")
    private String codeHash;
    @Column(name = "used_at")
    private Instant usedAt;

    protected RecoveryCode() {}

    public RecoveryCode(UUID id, UUID userId, String codeHash) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public String getCodeHash() { return codeHash; }
    public boolean isUsed() { return usedAt != null; }
    public void markUsed(Instant now) { this.usedAt = now; }
}
