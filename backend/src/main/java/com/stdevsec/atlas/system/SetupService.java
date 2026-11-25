package com.stdevsec.atlas.system;

import com.stdevsec.atlas.identity.UserDirectory;
import com.stdevsec.atlas.identity.UserSummary;
import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.BadRequestException;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.workspace.WorkspaceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-run setup protected by a one-time token printed to the backend log (ADR 0009, T-01).
 * Only the token's hash is kept, in memory; a restart while setup is pending issues a new one.
 */
@Service
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);

    private final InstanceSettings settings;
    private final UserDirectory users;
    private final WorkspaceService workspaces;
    private final ApplicationEventPublisher events;
    private volatile byte[] tokenHash;

    SetupService(InstanceSettings settings, UserDirectory users, WorkspaceService workspaces,
            ApplicationEventPublisher events) {
        this.settings = settings;
        this.users = users;
        this.workspaces = workspaces;
        this.events = events;
    }

    /** Issues (or re-issues) the token and prints it to the log. Never returns it over HTTP. */
    @EventListener(ApplicationReadyEvent.class)
    public void issueTokenIfPending() {
        if (isPending()) {
            byte[] raw = new byte[16];
            new SecureRandom().nextBytes(raw);
            String token = HexFormat.of().formatHex(raw);
            tokenHash = sha256(token);
            log.warn("""

                    ================================================================
                     ST Atlas first-run setup is pending.
                     Open the application and enter this setup token:

                         {}

                     The token is valid until setup completes or the backend restarts.
                    ================================================================""", token);
        }
    }

    public boolean isPending() {
        return !settings.setupCompleted();
    }

    public record SetupRequest(String token, String organizationName, String username, String displayName,
            String password, String locale, String timeZone, String workspaceName) {}

    @Transactional
    public synchronized UserSummary complete(SetupRequest req) {
        if (!isPending()) {
            throw new NotFoundException("Setup");
        }
        if (tokenHash == null || req.token() == null
                || !MessageDigest.isEqual(tokenHash, sha256(req.token().trim().toLowerCase()))) {
            throw new BadRequestException("Invalid setup token");
        }
        UserSummary admin = users.createUser(req.username().trim(), req.displayName().trim(), req.password(),
                req.locale(), req.timeZone(), true);
        workspaces.create(req.workspaceName(), admin.id());
        settings.put(InstanceSettings.ORGANIZATION_NAME, req.organizationName().trim());
        settings.put(InstanceSettings.DEFAULT_LOCALE, req.locale());
        settings.put(InstanceSettings.DEFAULT_TIME_ZONE, req.timeZone());
        settings.put(InstanceSettings.SETUP_COMPLETED, "true");
        tokenHash = null;
        events.publishEvent(AuditRecord.of(null, admin.id(), "SETUP_COMPLETED", "instance", null));
        log.info("First-run setup completed by '{}'", admin.username());
        return admin;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
