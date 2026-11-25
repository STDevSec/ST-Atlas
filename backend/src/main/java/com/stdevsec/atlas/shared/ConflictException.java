package com.stdevsec.atlas.shared;

/** The request is valid but conflicts with the current state (lifecycle, versions). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
