package com.rohit.labelbuilder.model.style;

/** How a barcode's check digit is supplied (lbl-format.md §4.3). */
public enum CheckDigitMode {
    /** The engine computes and appends the check digit. */
    AUTO,
    /** No check digit. */
    NONE,
    /** The value already includes a valid check digit. */
    PROVIDED
}
