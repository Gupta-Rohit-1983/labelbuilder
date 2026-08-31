package com.rohit.labelbuilder.model.meta;

/**
 * The editor family a {@link PropertyDescriptor} maps to. The Property Inspector (Phase 9) picks a
 * concrete control from this, rather than reflecting over Java types — a stable, closed vocabulary
 * that survives model refactors.
 */
public enum PropertyKind {
    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    COLOR,
    ENUM,
    FONT
}
