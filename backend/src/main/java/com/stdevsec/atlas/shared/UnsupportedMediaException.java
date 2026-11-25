package com.stdevsec.atlas.shared;

/** Uploaded content is not of an allowed type (detected from bytes, not the name). */
public class UnsupportedMediaException extends RuntimeException {
    public UnsupportedMediaException(String message) {
        super(message);
    }
}
