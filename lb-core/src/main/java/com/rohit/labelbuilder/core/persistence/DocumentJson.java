package com.rohit.labelbuilder.core.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rohit.labelbuilder.model.document.GridSpec;
import com.rohit.labelbuilder.model.document.Guides;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.document.Layer;
import com.rohit.labelbuilder.model.document.Margins;
import com.rohit.labelbuilder.model.document.Orientation;
import com.rohit.labelbuilder.model.document.Stock;
import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.ElementProperties;
import com.rohit.labelbuilder.model.element.EllipseElement;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.LineElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.model.style.AutoFit;
import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.DashStyle;
import com.rohit.labelbuilder.model.style.Fill;
import com.rohit.labelbuilder.model.style.FontSpec;
import com.rohit.labelbuilder.model.style.Hri;
import com.rohit.labelbuilder.model.style.HriPosition;
import com.rohit.labelbuilder.model.style.ImageFit;
import com.rohit.labelbuilder.model.style.LineCap;
import com.rohit.labelbuilder.model.style.Monochrome;
import com.rohit.labelbuilder.model.style.MonochromeMethod;
import com.rohit.labelbuilder.model.style.RgbaColor;
import com.rohit.labelbuilder.model.style.Stroke;
import com.rohit.labelbuilder.model.style.Symbology;
import com.rohit.labelbuilder.model.style.TextAlign;
import com.rohit.labelbuilder.model.style.VerticalAlign;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps {@link LabelDocument} to and from JSON (lbl-format.md §3–4) by hand over Jackson's tree model.
 * Hand-mapping — rather than annotating the model — keeps lb-model framework-free and gives exact
 * control over field order, so writing the same document twice yields byte-identical output
 * (invariant §8.1). Unknown fields are ignored on read, giving the format forward tolerance (§7).
 *
 * <p>Colours are {@code #RRGGBB[AA]}; enums are their {@code name()}; every dimension is millimetres.
 */
public final class DocumentJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private DocumentJson() {}

    // --- document ---

    public static byte[] writeBytes(LabelDocument document) {
        try {
            return MAPPER.writeValueAsBytes(toNode(document));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new LabelFileException("could not serialise document", e);
        }
    }

    public static LabelDocument readBytes(byte[] json) {
        try {
            return fromNode(MAPPER.readTree(json));
        } catch (LabelFileException e) {
            throw e;
        } catch (Exception e) {
            throw new LabelFileException("could not parse document.json", e);
        }
    }

    public static ObjectNode toNode(LabelDocument d) {
        ObjectNode o = NODES.objectNode();
        o.put("id", d.id());
        o.put("name", d.name());
        o.set("stock", stock(d.stock()));
        o.set("grid", grid(d.grid()));
        o.set("guides", guides(d.guides()));
        o.set("layers", layers(d.layers()));
        o.set("elements", elements(d.elements()));
        return o;
    }

    public static LabelDocument fromNode(JsonNode n) {
        return new LabelDocument(
                text(n, "id"),
                text(n, "name"),
                readStock(n.get("stock")),
                readGrid(n.get("grid")),
                readGuides(n.get("guides")),
                readLayers(n.get("layers")),
                readElements(n.get("elements")));
    }

    // --- stock / grid / guides / layers ---

    private static ObjectNode stock(Stock s) {
        ObjectNode o = NODES.objectNode();
        o.put("widthMm", s.widthMm());
        o.put("heightMm", s.heightMm());
        o.put("orientation", s.orientation().name());
        ObjectNode m = NODES.objectNode();
        m.put("top", s.marginsMm().topMm());
        m.put("right", s.marginsMm().rightMm());
        m.put("bottom", s.marginsMm().bottomMm());
        m.put("left", s.marginsMm().leftMm());
        o.set("marginsMm", m);
        o.put("backgroundColor", s.backgroundColor().toHex());
        if (s.presetId() == null) {
            o.putNull("presetId");
        } else {
            o.put("presetId", s.presetId());
        }
        return o;
    }

    private static Stock readStock(JsonNode n) {
        JsonNode m = n.get("marginsMm");
        Margins margins = new Margins(
                m.get("top").asDouble(),
                m.get("right").asDouble(),
                m.get("bottom").asDouble(),
                m.get("left").asDouble());
        return new Stock(
                n.get("widthMm").asDouble(),
                n.get("heightMm").asDouble(),
                Orientation.valueOf(n.get("orientation").asText()),
                margins,
                RgbaColor.parse(n.get("backgroundColor").asText()),
                n.hasNonNull("presetId") ? n.get("presetId").asText() : null);
    }

    private static ObjectNode grid(GridSpec g) {
        ObjectNode o = NODES.objectNode();
        o.put("visible", g.visible());
        o.put("spacingMm", g.spacingMm());
        o.put("snap", g.snap());
        return o;
    }

    private static GridSpec readGrid(JsonNode n) {
        return new GridSpec(
                n.get("visible").asBoolean(),
                n.get("spacingMm").asDouble(),
                n.get("snap").asBoolean());
    }

    private static ObjectNode guides(Guides g) {
        ObjectNode o = NODES.objectNode();
        o.set("vertical", doubles(g.vertical()));
        o.set("horizontal", doubles(g.horizontal()));
        return o;
    }

    private static Guides readGuides(JsonNode n) {
        return new Guides(readDoubles(n.get("vertical")), readDoubles(n.get("horizontal")));
    }

    private static ArrayNode layers(List<Layer> layers) {
        ArrayNode a = NODES.arrayNode();
        for (Layer l : layers) {
            ObjectNode o = NODES.objectNode();
            o.put("id", l.id());
            o.put("name", l.name());
            o.put("visible", l.visible());
            o.put("locked", l.locked());
            a.add(o);
        }
        return a;
    }

    private static List<Layer> readLayers(JsonNode n) {
        List<Layer> out = new ArrayList<>();
        for (JsonNode l : n) {
            out.add(new Layer(
                    l.get("id").asText(),
                    l.get("name").asText(),
                    l.get("visible").asBoolean(),
                    l.get("locked").asBoolean()));
        }
        return out;
    }

    // --- elements ---

    private static ArrayNode elements(List<LabelElement> elements) {
        ArrayNode a = NODES.arrayNode();
        for (LabelElement e : elements) {
            a.add(element(e));
        }
        return a;
    }

    private static List<LabelElement> readElements(JsonNode n) {
        List<LabelElement> out = new ArrayList<>();
        for (JsonNode e : n) {
            out.add(readElement(e));
        }
        return out;
    }

    private static ObjectNode element(LabelElement e) {
        ObjectNode o = NODES.objectNode();
        // common (order matches lbl-format.md §4.1), with "type" as the second key
        o.put("id", e.id());
        o.put("type", typeOf(e));
        o.put("name", e.name());
        o.put("layerId", e.layerId());
        o.set("bounds", bounds(e.bounds()));
        o.put("rotation", e.rotationDeg());
        o.put("locked", e.locked());
        o.put("visible", e.visible());
        if (e.properties().printCondition() == null) {
            o.putNull("printCondition");
        } else {
            o.put("printCondition", e.properties().printCondition());
        }
        // type-specific
        switch (e) {
            case TextElement t -> writeText(o, t);
            case RectangleElement r -> writeRectangle(o, r);
            case EllipseElement el -> writeEllipse(o, el);
            case LineElement l -> writeLine(o, l);
            case ImageElement i -> writeImage(o, i);
            case BarcodeElement b -> writeBarcode(o, b);
            case GroupElement g -> o.set("children", elements(g.children()));
        }
        return o;
    }

    private static LabelElement readElement(JsonNode n) {
        ElementProperties p = readCommon(n);
        String type = n.get("type").asText();
        return switch (type) {
            case "text" -> readText(n, p);
            case "rectangle" -> readRectangle(n, p);
            case "ellipse" -> readEllipse(n, p);
            case "line" -> readLine(n, p);
            case "image" -> readImage(n, p);
            case "barcode" -> readBarcode(n, p);
            case "group" -> new GroupElement(p, readElements(n.get("children")));
            default -> throw new LabelFileException("unknown element type: " + type);
        };
    }

    private static String typeOf(LabelElement e) {
        return switch (e) {
            case TextElement ignored -> "text";
            case RectangleElement ignored -> "rectangle";
            case EllipseElement ignored -> "ellipse";
            case LineElement ignored -> "line";
            case ImageElement ignored -> "image";
            case BarcodeElement ignored -> "barcode";
            case GroupElement ignored -> "group";
        };
    }

    private static ElementProperties readCommon(JsonNode n) {
        return new ElementProperties(
                n.get("id").asText(),
                n.get("name").asText(),
                n.get("layerId").asText(),
                readBounds(n.get("bounds")),
                n.get("rotation").asDouble(),
                n.get("locked").asBoolean(),
                n.get("visible").asBoolean(),
                n.hasNonNull("printCondition") ? n.get("printCondition").asText() : null);
    }

    private static void writeText(ObjectNode o, TextElement t) {
        o.put("value", t.value());
        o.set("font", font(t.font()));
        o.put("color", t.color().toHex());
        o.put("align", t.align().name());
        o.put("verticalAlign", t.verticalAlign().name());
        o.put("lineSpacing", t.lineSpacing());
        o.put("wrap", t.wrap());
        o.put("autoFit", t.autoFit().name());
        o.put("characterSpacing", t.characterSpacing());
    }

    private static TextElement readText(JsonNode n, ElementProperties p) {
        return new TextElement(
                p,
                n.get("value").asText(),
                readFont(n.get("font")),
                RgbaColor.parse(n.get("color").asText()),
                TextAlign.valueOf(n.get("align").asText()),
                VerticalAlign.valueOf(n.get("verticalAlign").asText()),
                n.get("lineSpacing").asDouble(),
                n.get("wrap").asBoolean(),
                AutoFit.valueOf(n.get("autoFit").asText()),
                n.get("characterSpacing").asDouble());
    }

    private static void writeRectangle(ObjectNode o, RectangleElement r) {
        o.set("stroke", stroke(r.stroke()));
        o.set("fill", fill(r.fill()));
        o.put("cornerRadiusMm", r.cornerRadiusMm());
    }

    private static RectangleElement readRectangle(JsonNode n, ElementProperties p) {
        return new RectangleElement(
                p,
                readStroke(n.get("stroke")),
                readFill(n.get("fill")),
                n.get("cornerRadiusMm").asDouble());
    }

    private static void writeEllipse(ObjectNode o, EllipseElement el) {
        o.set("stroke", stroke(el.stroke()));
        o.set("fill", fill(el.fill()));
    }

    private static EllipseElement readEllipse(JsonNode n, ElementProperties p) {
        return new EllipseElement(p, readStroke(n.get("stroke")), readFill(n.get("fill")));
    }

    private static void writeLine(ObjectNode o, LineElement l) {
        o.set("stroke", stroke(l.stroke()));
        o.put("lineCap", l.lineCap().name());
    }

    private static LineElement readLine(JsonNode n, ElementProperties p) {
        return new LineElement(
                p, readStroke(n.get("stroke")), LineCap.valueOf(n.get("lineCap").asText()));
    }

    private static void writeImage(ObjectNode o, ImageElement i) {
        o.put("assetPath", i.assetPath());
        o.put("fitMode", i.fitMode().name());
        ObjectNode m = NODES.objectNode();
        m.put("enabled", i.monochrome().enabled());
        m.put("method", i.monochrome().method().name());
        m.put("threshold", i.monochrome().threshold());
        o.set("monochrome", m);
        o.put("opacity", i.opacity());
    }

    private static ImageElement readImage(JsonNode n, ElementProperties p) {
        JsonNode m = n.get("monochrome");
        Monochrome mono = new Monochrome(
                m.get("enabled").asBoolean(),
                MonochromeMethod.valueOf(m.get("method").asText()),
                m.get("threshold").asInt());
        return new ImageElement(
                p,
                n.get("assetPath").asText(),
                ImageFit.valueOf(n.get("fitMode").asText()),
                mono,
                n.get("opacity").asDouble());
    }

    private static void writeBarcode(ObjectNode o, BarcodeElement b) {
        o.put("symbology", b.symbology().name());
        o.put("value", b.value());
        o.put("xDimensionMm", b.xDimensionMm());
        o.put("barHeightMm", b.barHeightMm());
        o.put("quietZoneMm", b.quietZoneMm());
        o.put("checkDigit", b.checkDigit().name());
        ObjectNode h = NODES.objectNode();
        h.put("position", b.hri().position().name());
        h.set("font", font(b.hri().font()));
        h.put("showCheckDigit", b.hri().showCheckDigit());
        if (b.hri().customText() == null) {
            h.putNull("customText");
        } else {
            h.put("customText", b.hri().customText());
        }
        o.set("hri", h);
        o.put("rotationQuadrant", b.rotationQuadrant());
    }

    private static BarcodeElement readBarcode(JsonNode n, ElementProperties p) {
        JsonNode h = n.get("hri");
        Hri hri = new Hri(
                HriPosition.valueOf(h.get("position").asText()),
                readFont(h.get("font")),
                h.get("showCheckDigit").asBoolean(),
                h.hasNonNull("customText") ? h.get("customText").asText() : null);
        return new BarcodeElement(
                p,
                Symbology.valueOf(n.get("symbology").asText()),
                n.get("value").asText(),
                n.get("xDimensionMm").asDouble(),
                n.get("barHeightMm").asDouble(),
                n.get("quietZoneMm").asDouble(),
                CheckDigitMode.valueOf(n.get("checkDigit").asText()),
                hri,
                n.get("rotationQuadrant").asInt());
    }

    // --- shared value types ---

    private static ObjectNode bounds(Bounds b) {
        ObjectNode o = NODES.objectNode();
        o.put("xMm", b.xMm());
        o.put("yMm", b.yMm());
        o.put("widthMm", b.widthMm());
        o.put("heightMm", b.heightMm());
        return o;
    }

    private static Bounds readBounds(JsonNode n) {
        return new Bounds(
                n.get("xMm").asDouble(),
                n.get("yMm").asDouble(),
                n.get("widthMm").asDouble(),
                n.get("heightMm").asDouble());
    }

    private static ObjectNode font(FontSpec f) {
        ObjectNode o = NODES.objectNode();
        o.put("family", f.family());
        o.put("sizePt", f.sizePt());
        o.put("bold", f.bold());
        o.put("italic", f.italic());
        o.put("underline", f.underline());
        return o;
    }

    private static FontSpec readFont(JsonNode n) {
        return new FontSpec(
                n.get("family").asText(),
                n.get("sizePt").asDouble(),
                n.get("bold").asBoolean(),
                n.get("italic").asBoolean(),
                n.get("underline").asBoolean());
    }

    private static ObjectNode stroke(Stroke s) {
        ObjectNode o = NODES.objectNode();
        o.put("color", s.color().toHex());
        o.put("widthMm", s.widthMm());
        o.put("dash", s.dash().name());
        return o;
    }

    private static Stroke readStroke(JsonNode n) {
        return new Stroke(
                RgbaColor.parse(n.get("color").asText()),
                n.get("widthMm").asDouble(),
                DashStyle.valueOf(n.get("dash").asText()));
    }

    private static ObjectNode fill(Fill f) {
        ObjectNode o = NODES.objectNode();
        o.put("color", f.color().toHex());
        o.put("enabled", f.enabled());
        return o;
    }

    private static Fill readFill(JsonNode n) {
        return new Fill(
                RgbaColor.parse(n.get("color").asText()), n.get("enabled").asBoolean());
    }

    private static ArrayNode doubles(List<Double> values) {
        ArrayNode a = NODES.arrayNode();
        for (double v : values) {
            a.add(v);
        }
        return a;
    }

    private static List<Double> readDoubles(JsonNode n) {
        List<Double> out = new ArrayList<>();
        for (JsonNode v : n) {
            out.add(v.asDouble());
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        return n.get(field).asText();
    }
}
