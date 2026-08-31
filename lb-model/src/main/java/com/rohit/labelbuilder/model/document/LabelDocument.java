package com.rohit.labelbuilder.model.document;

import com.rohit.labelbuilder.model.element.LabelElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The whole label: its stock, layers, grid, guides and the ordered elements. This is the model's
 * aggregate root — the thing a {@code .lbl} file serialises (Phase 7d) and Commands transform
 * (Phase 7c).
 *
 * <p>Immutable. Every edit returns a new document; the {@code elements} list is ordered
 * <b>back-to-front</b>, so the list index <i>is</i> the z-order (lbl-format.md §3). Both lists are
 * defensively copied and unmodifiable. Cross-reference integrity (every {@code layerId} resolves,
 * no orphan assets) is a persistence-time invariant asserted in Phase 7d, not enforced here, so a
 * document can be built up incrementally by the editor.
 */
public record LabelDocument(
        String id,
        String name,
        Stock stock,
        GridSpec grid,
        Guides guides,
        List<Layer> layers,
        List<LabelElement> elements) {

    public LabelDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(guides, "guides");
        layers = List.copyOf(layers);
        elements = List.copyOf(elements);
    }

    /** An empty document on the given stock with a single default "Content" layer. */
    public static LabelDocument blank(String id, String name, Stock stock) {
        return new LabelDocument(
                id,
                name,
                stock,
                GridSpec.defaults(),
                Guides.none(),
                List.of(Layer.of("layer-1", "Content")),
                List.of());
    }

    /** The element with the given id, if present. */
    public Optional<LabelElement> findElement(String elementId) {
        return elements.stream().filter(e -> e.id().equals(elementId)).findFirst();
    }

    /** A copy with {@code element} appended at the top of the z-order. */
    public LabelDocument addElement(LabelElement element) {
        Objects.requireNonNull(element, "element");
        List<LabelElement> next = new ArrayList<>(elements);
        next.add(element);
        return withElements(next);
    }

    /** A copy with the element of the given id removed (unchanged if absent). */
    public LabelDocument removeElement(String elementId) {
        List<LabelElement> next = new ArrayList<>(elements);
        next.removeIf(e -> e.id().equals(elementId));
        return withElements(next);
    }

    /**
     * A copy with the element sharing {@code replacement}'s id swapped in place, keeping its z-order.
     *
     * @throws IllegalArgumentException if no element has that id
     */
    public LabelDocument replaceElement(LabelElement replacement) {
        Objects.requireNonNull(replacement, "replacement");
        List<LabelElement> next = new ArrayList<>(elements);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(replacement.id())) {
                next.set(i, replacement);
                return withElements(next);
            }
        }
        throw new IllegalArgumentException("no element with id " + replacement.id());
    }

    public LabelDocument withElements(List<LabelElement> newElements) {
        return new LabelDocument(id, name, stock, grid, guides, layers, newElements);
    }

    public LabelDocument withStock(Stock newStock) {
        return new LabelDocument(id, name, newStock, grid, guides, layers, elements);
    }

    public LabelDocument withName(String newName) {
        return new LabelDocument(id, newName, stock, grid, guides, layers, elements);
    }

    public LabelDocument withLayers(List<Layer> newLayers) {
        return new LabelDocument(id, name, stock, grid, guides, newLayers, elements);
    }

    public LabelDocument withGrid(GridSpec newGrid) {
        return new LabelDocument(id, name, stock, newGrid, guides, layers, elements);
    }

    public LabelDocument withGuides(Guides newGuides) {
        return new LabelDocument(id, name, stock, grid, newGuides, layers, elements);
    }
}
