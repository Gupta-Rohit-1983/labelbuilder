package com.rohit.labelbuilder.model.element;

import com.rohit.labelbuilder.model.style.ImageFit;
import com.rohit.labelbuilder.model.style.Monochrome;
import java.util.Objects;

/**
 * A raster image (lbl-format.md §4.5). The pixels are not held here: {@code assetPath} references a
 * binary inside the {@code .lbl} archive ({@code assets/…}), keeping the model light and the
 * document self-contained when the template is emailed.
 */
public record ImageElement(
        ElementProperties properties, String assetPath, ImageFit fitMode, Monochrome monochrome, double opacity)
        implements LabelElement {

    public ImageElement {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(assetPath, "assetPath");
        Objects.requireNonNull(fitMode, "fitMode");
        Objects.requireNonNull(monochrome, "monochrome");
        if (opacity < 0 || opacity > 1 || !Double.isFinite(opacity)) {
            throw new IllegalArgumentException("opacity must be 0..1, was " + opacity);
        }
    }

    /** A fully-opaque, colour, contain-fitted image referencing the given archive asset. */
    public static ImageElement of(ElementProperties properties, String assetPath) {
        return new ImageElement(properties, assetPath, ImageFit.CONTAIN, Monochrome.disabled(), 1.0);
    }

    public ImageElement withAssetPath(String newAssetPath) {
        return new ImageElement(properties, newAssetPath, fitMode, monochrome, opacity);
    }

    @Override
    public ImageElement withProperties(ElementProperties newProperties) {
        return new ImageElement(newProperties, assetPath, fitMode, monochrome, opacity);
    }
}
