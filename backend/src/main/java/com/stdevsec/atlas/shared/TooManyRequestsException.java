package com.stdevsec.atlas.shared;

/** Rate limit exceeded. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
