package com.stdevsec.atlas.identity.internal;

import com.stdevsec.atlas.identity.UserSummary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    private UUID id;
    private String username;
    @Column(name = "display_name")
    private String displayName;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "mfa_secret_enc")
    private String mfaSecretEnc;
    @Column(name = "mfa_enabled")
    private boolean mfaEnabled;
    private String locale;
    @Column(name = "time_zone")
    private String timeZone;
    @Column(name = "is_admin")
    private boolean admin;
    private boolean enabled = true;
    @Column(name = "failed_logins")
    private int failedLogins;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected AppUser() {}

    public AppUser(UUID id, String username, String displayName, String passwordHash, String locale,
            String timeZone, boolean admin) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.locale = locale;
        this.timeZone = timeZone;
        this.admin = admin;
    }

    public UserSummary toSummary() {
        return new UserSummary(id, username, displayName, locale, timeZone, admin, mfaEnabled);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a failed attempt; locks the account after {@value #MAX_FAILURES} in a row. */
    public void registerFailure(Instant now) {
        failedLogins++;
        if (failedLogins >= MAX_FAILURES) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedLogins = 0;
        }
    }

    public void registerSuccess() {
        failedLogins = 0;
        lockedUntil = null;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public String getMfaSecretEnc() { return mfaSecretEnc; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public boolean isAdmin() { return admin; }
    public boolean isEnabled() { return enabled; }
    public String getLocale() { return locale; }

    public void setPasswordHash(String hash) { this.passwordHash = hash; }
    public void setLocale(String locale) { this.locale = locale; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public void enableMfa(String encryptedSecret) {
        this.mfaSecretEnc = encryptedSecret;
        this.mfaEnabled = true;
    }

    public void disableMfa() {
        this.mfaSecretEnc = null;
        this.mfaEnabled = false;
    }
}
