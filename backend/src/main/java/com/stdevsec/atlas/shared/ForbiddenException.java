package com.stdevsec.atlas.shared;

/** The caller can see the resource but their role does not allow the operation. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
