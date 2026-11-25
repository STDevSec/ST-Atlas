package com.stdevsec.atlas.shared;

/**
 * The resource does not exist <em>or the caller may not know that it exists</em>.
 * Inaccessible resources in other workspaces are reported exactly like missing
 * ones, so ids cannot be probed (THREAT_MODEL T-05).
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String what) {
        super(what + " not found");
    }
}
