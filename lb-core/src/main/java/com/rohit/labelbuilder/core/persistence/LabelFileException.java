package com.rohit.labelbuilder.core.persistence;

/** Thrown when a {@code .lbl} archive cannot be read: corrupt, missing parts, or a newer schema. */
public class LabelFileException extends RuntimeException {

    public LabelFileException(String message) {
        super(message);
    }

    public LabelFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
