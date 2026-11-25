package com.stdevsec.atlas.identity.internal;

import com.stdevsec.atlas.shared.BadRequestException;

/**
 * Length-based policy (NIST SP 800-63B): at least 12 characters, at most 128,
 * not containing the username. No composition rules, no forced rotation.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {}

    public static void check(String username, String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BadRequestException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BadRequestException("Password must be at most " + MAX_LENGTH + " characters");
        }
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            throw new BadRequestException("Password must not contain the username");
        }
    }
}
