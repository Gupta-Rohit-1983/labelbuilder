package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Replace a layer in place (rename, or toggle its visibility or lock), keeping its position in the
 * layer order. Merges by layer id, so dragging a visibility toggle is one undo step.
 *
 * @throws IllegalArgumentException from {@link #apply} if no layer has that id
 */
public record UpdateLayerCommand(Layer layer, String label) implements Command {

    public UpdateLayerCommand {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(label, "label");
    }

    public static UpdateLayerCommand of(Layer layer) {
        return new UpdateLayerCommand(layer, "Change Layer");
    }

    @Override
    public String mergeKey() {
        return "layer:" + layer.id();
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        List<Layer> layers = new ArrayList<>(document.layers());
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).id().equals(layer.id())) {
                layers.set(i, layer);
                return document.withLayers(layers);
            }
        }
        throw new IllegalArgumentException("no layer with id " + layer.id());
    }
}
