package com.stdevsec.atlas.identity.internal;

import com.stdevsec.atlas.identity.AtlasPrincipal;
import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.BadRequestException;
import com.stdevsec.atlas.shared.Ids;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Credential verification, MFA enrolment and password changes. */
@Service
public class AuthService {

    /** Outcome of a login attempt. Deliberately coarse: no hint about which factor failed. */
    public sealed interface LoginResult {
        record Success(AtlasPrincipal principal) implements LoginResult {}
        record MfaRequired() implements LoginResult {}
        record Failure() implements LoginResult {}
    }

    // Argon2id hash of a random value, used to equalise timing for unknown users.
    private final String dummyHash;
    private final AppUserRepository users;
    private final RecoveryCodeRepository recoveryCodes;
    private final PasswordEncoder encoder;
    private final InstanceSecrets secrets;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Map<UUID, Long> lastTotpStep = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    AuthService(AppUserRepository users, RecoveryCodeRepository recoveryCodes, PasswordEncoder encoder,
            InstanceSecrets secrets, ApplicationEventPublisher events) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.encoder = encoder;
        this.secrets = secrets;
        this.events = events;
        this.clock = Clock.systemUTC();
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public LoginResult login(String username, String password, String totp, String recoveryCode) {
        Instant now = clock.instant();
        Optional<AppUser> found = users.findByUsernameIgnoreCase(username == null ? "" : username);
        if (found.isEmpty()) {
            encoder.matches(password == null ? "" : password, dummyHash); // same cost as a real check
            audit(null, "LOGIN_FAILURE", Map.of("reason", "credentials"));
            return new LoginResult.Failure();
        }
        AppUser user = found.get();
        if (!user.isEnabled() || user.isLocked(now) || !encoder.matches(password == null ? "" : password,
                user.getPasswordHash())) {
            if (user.isEnabled() && !user.isLocked(now)) {
                user.registerFailure(now);
            }
            audit(user.getId(), "LOGIN_FAILURE", Map.of("reason", "credentials"));
            return new LoginResult.Failure();
        }
        if (user.isMfaEnabled()) {
            boolean secondFactor = verifyTotp(user, totp, now) || consumeRecoveryCode(user, recoveryCode, now);
            if (!secondFactor) {
                if (isBlank(totp) && isBlank(recoveryCode)) {
                    return new LoginResult.MfaRequired();
                }
                user.registerFailure(now);
                audit(user.getId(), "LOGIN_FAILURE", Map.of("reason", "mfa"));
                return new LoginResult.Failure();
            }
        }
        user.registerSuccess();
        audit(user.getId(), "LOGIN_SUCCESS", Map.of());
        return new LoginResult.Success(new AtlasPrincipal(user.getId(), user.getUsername(), user.isAdmin()));
    }

    private boolean verifyTotp(AppUser user, String code, Instant now) {
        if (isBlank(code) || user.getMfaSecretEnc() == null) {
            return false;
        }
        long step = Totp.verify(secrets.decrypt(user.getMfaSecretEnc()), code.trim(), now);
        if (step < 0) {
            return false;
        }
        // Reject replay of a code (or an older one) already used for this user.
        Long last = lastTotpStep.get(user.getId());
        if (last != null && step <= last) {
            return false;
        }
        lastTotpStep.put(user.getId(), step);
        return true;
    }

    private boolean consumeRecoveryCode(AppUser user, String code, Instant now) {
        if (isBlank(code)) {
            return false;
        }
        String hash = sha256(normalizeRecovery(code));
        for (RecoveryCode rc : recoveryCodes.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (MessageDigest.isEqual(rc.getCodeHash().getBytes(), hash.getBytes())) {
                rc.markUsed(now);
                audit(user.getId(), "RECOVERY_CODE_USED", Map.of());
                return true;
            }
        }
        return false;
    }

    /** Starts enrolment: returns a new secret (not yet active) and its otpauth URI. */
    public MfaEnrolment beginMfa(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow();
        String secret = Totp.newSecret();
        return new MfaEnrolment(secret, Totp.otpauthUri("ST Atlas", user.getUsername(), secret));
    }

    /** Activates MFA if the code matches the pending secret; returns one-time recovery codes. */
    @Transactional
    public List<String> enableMfa(UUID userId, String pendingSecret, String code) {
        if (pendingSecret == null || Totp.verify(pendingSecret, code, clock.instant()) < 0) {
            throw new BadRequestException("Invalid verification code");
        }
        AppUser user = users.findById(userId).orElseThrow();
        user.enableMfa(secrets.encrypt(pendingSecret));
        recoveryCodes.deleteByUserId(userId);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String raw = HexFormat.of().formatHex(randomBytes(5));
            String display = raw.substring(0, 5) + "-" + raw.substring(5);
            codes.add(display);
            recoveryCodes.save(new RecoveryCode(Ids.newId(), userId, sha256(normalizeRecovery(display))));
        }
        audit(userId, "MFA_ENABLED", Map.of());
        return codes;
    }

    @Transactional
    public void disableMfa(UUID userId, String password) {
        AppUser user = users.findById(userId).orElseThrow();
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Invalid password");
        }
        user.disableMfa();
        recoveryCodes.deleteByUserId(userId);
        audit(userId, "MFA_DISABLED", Map.of());
    }

    @Transactional
    public void changePassword(UUID userId, String current, String next) {
        AppUser user = users.findById(userId).orElseThrow();
        if (!encoder.matches(current, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        PasswordPolicy.check(user.getUsername(), next);
        user.setPasswordHash(encoder.encode(next));
        audit(userId, "PASSWORD_CHANGED", Map.of());
    }

    @Transactional
    public void updatePreferences(UUID userId, String locale, String timeZone) {
        AppUser user = users.findById(userId).orElseThrow();
        if (locale != null) {
            user.setLocale(locale);
        }
        if (timeZone != null) {
            user.setTimeZone(timeZone);
        }
    }

    public record MfaEnrolment(String secret, String otpauthUri) {}

    private void audit(UUID actor, String action, Map<String, Object> details) {
        events.publishEvent(new AuditRecord(null, actor, action, "user", actor, details));
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        random.nextBytes(b);
        return b;
    }

    private static String normalizeRecovery(String code) {
        return code.trim().toLowerCase().replace("-", "").replace(" ", "");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
