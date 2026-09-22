package com.rohit.labelbuilder.core.command;

import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Add a layer to the document.
 *
 * @throws IllegalArgumentException from {@link #apply} if the layer id is already taken
 */
public record AddLayerCommand(Layer layer) implements Command {

    public AddLayerCommand {
        Objects.requireNonNull(layer, "layer");
    }

    @Override
    public String label() {
        return "Add Layer";
    }

    @Override
    public LabelDocument apply(LabelDocument document) {
        if (document.layers().stream().anyMatch(l -> l.id().equals(layer.id()))) {
            throw new IllegalArgumentException("layer id already exists: " + layer.id());
        }
        List<Layer> layers = new ArrayList<>(document.layers());
        layers.add(layer);
        return document.withLayers(layers);
    }
}
