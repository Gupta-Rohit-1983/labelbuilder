package com.rohit.labelbuilder.model.style;

/**
 * Error-correction strength for 2D symbologies (lbl-format.md §4.4).
 *
 * <p>Higher levels survive more damage — smudging, abrasion, a torn label — at the cost of a larger
 * symbol for the same data. The four levels are QR's own vocabulary and apply directly to QR and
 * Aztec; PDF417 maps them onto its numeric levels, and Data Matrix ignores them entirely because
 * ECC200 is fixed. Those mappings live with the encoder, not here, so the model stays
 * framework-free.
 */
public enum ErrorCorrection {
    /** ~7% recoverable — smallest symbol; for clean, controlled surfaces. */
    L(7),
    /** ~15% recoverable — the usual default. */
    M(15),
    /** ~25% recoverable. */
    Q(25),
    /** ~30% recoverable — for labels that take a beating. */
    H(30);

    private final int approximateRecoveryPercent;

    ErrorCorrection(int approximateRecoveryPercent) {
        this.approximateRecoveryPercent = approximateRecoveryPercent;
    }

    /** Roughly how much of the symbol can be destroyed and still decode. */
    public int approximateRecoveryPercent() {
        return approximateRecoveryPercent;
    }
}
