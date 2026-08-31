package com.rohit.labelbuilder.core.persistence;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Enforces the cross-reference and safety invariants of a {@code .lbl} archive (lbl-format.md §8) —
 * the ones the model's per-object constructors can't see because they span the whole document:
 *
 * <ul>
 *   <li>§8.3 every image {@code assetPath} exists in the archive, and no asset is orphaned;
 *   <li>§8.4 every element's {@code layerId} resolves to a declared layer;
 *   <li>§8.5 no credential-shaped text is present.
 * </ul>
 *
 * (Numeric-dimension validity, §8.6, is already guaranteed by the model records at construction.)
 */
public final class LblValidator {

    /** Keys that must never appear in a saved template — credentials belong in the client store. */
    private static final List<String> CREDENTIAL_MARKERS =
            List.of("password", "pwd=", "\"secret\"", "connectionstring");

    private LblValidator() {}

    /** Validate structure and asset integrity; throws {@link LabelFileException} on any violation. */
    public static void validate(LabelPackage pkg) {
        LabelDocument doc = pkg.document();
        List<LabelElement> all = flatten(doc.elements());

        Set<String> layerIds = new HashSet<>();
        doc.layers().forEach(l -> layerIds.add(l.id()));
        for (LabelElement e : all) {
            if (!layerIds.contains(e.layerId())) {
                throw new LabelFileException("element '" + e.id() + "' references unknown layer '" + e.layerId() + "'");
            }
        }

        Set<String> referenced = new HashSet<>();
        for (LabelElement e : all) {
            if (e instanceof ImageElement img) {
                referenced.add(img.assetPath());
                if (!pkg.assets().containsKey(img.assetPath())) {
                    throw new LabelFileException(
                            "image '" + e.id() + "' references missing asset '" + img.assetPath() + "'");
                }
            }
        }
        for (String assetPath : pkg.assets().keySet()) {
            if (!referenced.contains(assetPath)) {
                throw new LabelFileException("orphan asset not referenced by any element: " + assetPath);
            }
        }
    }

    /** Fail if the bytes contain any credential-shaped marker (§8.5). Case-insensitive. */
    public static void assertNoCredentialLeak(byte[] bytes) {
        String haystack = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        for (String marker : CREDENTIAL_MARKERS) {
            if (haystack.contains(marker)) {
                throw new LabelFileException("archive appears to contain a credential ('" + marker + "')");
            }
        }
    }

    /** All elements, flattening group children. */
    private static List<LabelElement> flatten(List<LabelElement> elements) {
        List<LabelElement> out = new java.util.ArrayList<>();
        for (LabelElement e : elements) {
            out.add(e);
            if (e instanceof GroupElement g) {
                out.addAll(flatten(g.children()));
            }
        }
        return out;
    }
}
