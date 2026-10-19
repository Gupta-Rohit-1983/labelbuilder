package com.rohit.labelbuilder.barcode;

/**
 * Thrown when a value cannot be encoded: wrong length or characters for the symbology, a check digit
 * that does not verify, or a symbology this build cannot produce.
 *
 * <p>Unchecked, because an unencodable barcode is a data problem to surface to the user (in the
 * inspector, or as a print-time warning), not something every caller should handle.
 */
public class BarcodeException extends RuntimeException {

    public BarcodeException(String message) {
        super(message);
    }

    public BarcodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
