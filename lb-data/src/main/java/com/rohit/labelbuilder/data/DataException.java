package com.rohit.labelbuilder.data;

/**
 * Thrown when a data source cannot be reached, read, or makes no sense for the request.
 *
 * <p>Unchecked: a merge either has its data or it does not, and the handling point is the print job
 * or the designer's preview — not every frame of the call stack in between.
 */
public class DataException extends RuntimeException {

    public DataException(String message) {
        super(message);
    }

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }
}
