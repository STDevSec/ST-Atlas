package com.stdevsec.atlas.shared;

/** Semantically invalid input that Bean Validation cannot express. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
