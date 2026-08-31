package com.rohit.labelbuilder.core.persistence;

import com.rohit.labelbuilder.model.document.LabelDocument;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The full contents of a {@code .lbl} archive: the {@link LabelDocument} plus its embedded binaries.
 * The document model deliberately holds no image/font bytes (an {@code image} element only references
 * an {@code assets/…} path), so they travel here — keyed by that same archive path — keeping a saved
 * template self-contained (lbl-format.md §1).
 *
 * <p>Immutable; the asset map is defensively copied. {@code thumbnail} is the optional 256 px preview
 * PNG.
 */
public record LabelPackage(LabelDocument document, Map<String, byte[]> assets, byte[] thumbnail) {

    public LabelPackage {
        Objects.requireNonNull(document, "document");
        assets = Map.copyOf(assets);
        // thumbnail may be null
    }

    /** A package with no assets and no thumbnail. */
    public static LabelPackage of(LabelDocument document) {
        return new LabelPackage(document, Map.of(), null);
    }

    public LabelPackage withAssets(Map<String, byte[]> newAssets) {
        return new LabelPackage(document, newAssets, thumbnail);
    }

    public LabelPackage withThumbnail(byte[] newThumbnail) {
        return new LabelPackage(document, assets, newThumbnail);
    }

    public Optional<byte[]> thumbnailOptional() {
        return Optional.ofNullable(thumbnail);
    }
}
