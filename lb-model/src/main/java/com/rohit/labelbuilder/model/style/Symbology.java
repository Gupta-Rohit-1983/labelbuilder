package com.rohit.labelbuilder.model.style;

/**
 * Barcode symbologies, 1D and 2D, per lbl-format.md §4.9. Modelled now so the format and object
 * model are stable; the actual encoding engine (Barcode4J / ZXing) arrives in Phase 10.
 */
public enum Symbology {
    // 1D
    CODE_128,
    CODE_39,
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
    ITF_14,
    INTERLEAVED_2_OF_5,
    GS1_128,
    CODABAR,
    MSI,
    // 2D
    QR,
    GS1_QR,
    DATA_MATRIX,
    GS1_DATA_MATRIX,
    PDF417,
    AZTEC;

    /** True for the two-dimensional matrix symbologies (QR family, Data Matrix, PDF417, Aztec). */
    public boolean isTwoDimensional() {
        return switch (this) {
            case QR, GS1_QR, DATA_MATRIX, GS1_DATA_MATRIX, PDF417, AZTEC -> true;
            default -> false;
        };
    }
}
