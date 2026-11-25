package com.stdevsec.atlas.identity;

import java.util.UUID;

/** Public view of a user for other modules (never contains secrets). */
public record UserSummary(UUID id, String username, String displayName, String locale,
        String timeZone, boolean admin, boolean mfaEnabled) {}
