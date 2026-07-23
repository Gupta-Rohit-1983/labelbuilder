package com.rohit.labelbuilder.desktop.dock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.geometry.Orientation;
import javafx.geometry.Side;

/**
 * Serializes {@link DockState} to a compact single-line string for {@code Preferences} storage.
 *
 * <p>Format (version-prefixed so future revisions can migrate or discard):
 *
 * <pre>
 * v1|&lt;tree&gt;|id:SIDE,id:SIDE|id,id
 * tree  := C | G[id,id;selectedId] | S[H;0.25,0.5;{tree}{tree}...]
 * </pre>
 *
 * <p>Decoding is defensive by contract: any malformed text, unknown panel id or structural
 * violation yields {@link Optional#empty()} — callers fall back to the default layout. A stale
 * persisted layout must never be able to break startup.
 */
public final class DockStateCodec {

    private static final String VERSION = "v1";

    private DockStateCodec() {}

    // ---- encode -------------------------------------------------------------------------

    public static String encode(DockState state) {
        String hidden = state.autoHidden().entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue().name())
                .collect(Collectors.joining(","));
        String floating = String.join(",", state.floating());
        return VERSION + "|" + encodeNode(state.layout().root()) + "|" + hidden + "|" + floating;
    }

    private static String encodeNode(DockLayout.DockNode node) {
        return switch (node) {
            case DockLayout.DockNode.Center center -> "C";
            case DockLayout.DockNode.Group group ->
                "G[" + String.join(",", group.panelIds()) + ";" + group.selectedId() + "]";
            case DockLayout.DockNode.Split split ->
                "S[" + (split.orientation() == Orientation.HORIZONTAL ? "H" : "V") + ";"
                        + split.dividers().stream().map(String::valueOf).collect(Collectors.joining(","))
                        + ";"
                        + split.children().stream()
                                .map(child -> "{" + encodeNode(child) + "}")
                                .collect(Collectors.joining())
                        + "]";
        };
    }

    // ---- decode -------------------------------------------------------------------------

    /**
     * @param validPanelIds every panel id the text may reference — anything else invalidates it
     */
    public static Optional<DockState> decode(String text, Set<String> validPanelIds) {
        try {
            String[] parts = text.split("\\|", -1);
            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                return Optional.empty();
            }
            DockLayout layout = new DockLayout(parseNode(parts[1]));
            Map<String, Side> hidden = parseHidden(parts[2]);
            Set<String> floating = parseFloating(parts[3]);
            DockState state = new DockState(layout, hidden, floating);

            List<String> referenced = new ArrayList<>(layout.panelIds());
            referenced.addAll(hidden.keySet());
            referenced.addAll(floating);
            if (!validPanelIds.containsAll(referenced)) {
                return Optional.empty();
            }
            return Optional.of(state);
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static DockLayout.DockNode parseNode(String text) {
        if (text.equals("C")) {
            return new DockLayout.DockNode.Center();
        }
        if (text.startsWith("G[") && text.endsWith("]")) {
            String inner = text.substring(2, text.length() - 1);
            int semicolon = inner.lastIndexOf(';');
            List<String> ids = List.of(inner.substring(0, semicolon).split(","));
            return new DockLayout.DockNode.Group(ids, inner.substring(semicolon + 1));
        }
        if (text.startsWith("S[") && text.endsWith("]")) {
            String inner = text.substring(2, text.length() - 1);
            int firstSemi = inner.indexOf(';');
            int secondSemi = inner.indexOf(';', firstSemi + 1);
            Orientation orientation =
                    switch (inner.substring(0, firstSemi)) {
                        case "H" -> Orientation.HORIZONTAL;
                        case "V" -> Orientation.VERTICAL;
                        default -> throw new IllegalArgumentException("Bad orientation");
                    };
            String dividerPart = inner.substring(firstSemi + 1, secondSemi);
            List<Double> dividers = dividerPart.isEmpty()
                    ? List.of()
                    : java.util.Arrays.stream(dividerPart.split(","))
                            .map(Double::valueOf)
                            .toList();
            List<DockLayout.DockNode> children = parseChildren(inner.substring(secondSemi + 1));
            return new DockLayout.DockNode.Split(orientation, children, dividers);
        }
        throw new IllegalArgumentException("Unrecognised node: " + text);
    }

    /** Splits {@code {child}{child}...} on balanced braces and parses each child. */
    private static List<DockLayout.DockNode> parseChildren(String text) {
        List<DockLayout.DockNode> children = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    start = i + 1;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    children.add(parseNode(text.substring(start, i)));
                }
            }
        }
        if (depth != 0 || children.isEmpty()) {
            throw new IllegalArgumentException("Unbalanced children: " + text);
        }
        return children;
    }

    private static Map<String, Side> parseHidden(String text) {
        Map<String, Side> hidden = new LinkedHashMap<>();
        if (!text.isEmpty()) {
            for (String entry : text.split(",")) {
                int colon = entry.indexOf(':');
                hidden.put(entry.substring(0, colon), Side.valueOf(entry.substring(colon + 1)));
            }
        }
        return hidden;
    }

    private static Set<String> parseFloating(String text) {
        return text.isEmpty() ? Set.of() : new LinkedHashSet<>(List.of(text.split(",")));
    }
}
