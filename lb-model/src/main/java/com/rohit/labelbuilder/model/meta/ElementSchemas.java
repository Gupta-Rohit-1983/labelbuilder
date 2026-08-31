package com.rohit.labelbuilder.model.meta;

import com.rohit.labelbuilder.model.element.BarcodeElement;
import com.rohit.labelbuilder.model.element.EllipseElement;
import com.rohit.labelbuilder.model.element.GroupElement;
import com.rohit.labelbuilder.model.element.ImageElement;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.element.LineElement;
import com.rohit.labelbuilder.model.element.RectangleElement;
import com.rohit.labelbuilder.model.element.TextElement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The registry of {@link ElementSchema}s — one per concrete {@link LabelElement} type. Every element
 * type in the sealed hierarchy has a schema here (asserted by tests), so the Property Inspector and
 * serialiser can enumerate any element's editable properties by looking up its class.
 *
 * <p>Each schema is the shared {@link #common()} properties (name, visibility, geometry) followed by
 * the type's own. Getters and withers are explicit lambdas — no reflection — and all withers return
 * a new immutable element.
 */
public final class ElementSchemas {

    private static final String GENERAL = "General";
    private static final String GEOMETRY = "Geometry";
    private static final String TEXT = "Text";
    private static final String APPEARANCE = "Appearance";
    private static final String IMAGE = "Image";
    private static final String BARCODE = "Barcode";

    private static final Map<Class<? extends LabelElement>, ElementSchema> SCHEMAS = Map.of(
            TextElement.class, textSchema(),
            RectangleElement.class, rectangleSchema(),
            EllipseElement.class, ellipseSchema(),
            LineElement.class, lineSchema(),
            ImageElement.class, imageSchema(),
            BarcodeElement.class, barcodeSchema(),
            GroupElement.class, groupSchema());

    private ElementSchemas() {}

    /** The schema for an element instance. */
    public static ElementSchema schemaFor(LabelElement element) {
        return schemaFor(element.getClass());
    }

    /** The schema for an element type. */
    public static ElementSchema schemaFor(Class<? extends LabelElement> type) {
        ElementSchema schema = SCHEMAS.get(type);
        if (schema == null) {
            throw new IllegalArgumentException("no schema registered for " + type.getName());
        }
        return schema;
    }

    // --- shared common properties (every element) ---

    private static List<PropertyDescriptor> common() {
        List<PropertyDescriptor> p = new ArrayList<>();
        p.add(PropertyDescriptor.text(
                "name",
                "Name",
                GENERAL,
                LabelElement::name,
                (e, v) -> e.withProperties(e.properties().withName(v))));
        p.add(PropertyDescriptor.bool("visible", "Visible", GENERAL, LabelElement::visible, LabelElement::withVisible));
        p.add(PropertyDescriptor.bool("locked", "Locked", GENERAL, LabelElement::locked, LabelElement::withLocked));
        p.add(PropertyDescriptor.decimal(
                "x",
                "X (mm)",
                GEOMETRY,
                e -> e.bounds().xMm(),
                (e, v) -> e.withBounds(e.bounds().withPosition(v, e.bounds().yMm()))));
        p.add(PropertyDescriptor.decimal(
                "y",
                "Y (mm)",
                GEOMETRY,
                e -> e.bounds().yMm(),
                (e, v) -> e.withBounds(e.bounds().withPosition(e.bounds().xMm(), v))));
        p.add(PropertyDescriptor.decimal(
                        "width",
                        "Width (mm)",
                        GEOMETRY,
                        e -> e.bounds().widthMm(),
                        (e, v) -> e.withBounds(e.bounds().withSize(v, e.bounds().heightMm())))
                .withRange(0.0, null));
        p.add(PropertyDescriptor.decimal(
                        "height",
                        "Height (mm)",
                        GEOMETRY,
                        e -> e.bounds().heightMm(),
                        (e, v) -> e.withBounds(e.bounds().withSize(e.bounds().widthMm(), v)))
                .withRange(0.0, null));
        p.add(PropertyDescriptor.decimal(
                "rotation", "Rotation (°)", GEOMETRY, LabelElement::rotationDeg, LabelElement::withRotationDeg));
        return p;
    }

    private static ElementSchema schema(Class<? extends LabelElement> type, List<PropertyDescriptor> specific) {
        List<PropertyDescriptor> all = new ArrayList<>(common());
        all.addAll(specific);
        return new ElementSchema(type, all);
    }

    // --- per-type schemas ---

    private static ElementSchema textSchema() {
        List<PropertyDescriptor> p = new ArrayList<>();
        p.add(PropertyDescriptor.text("value", "Text", TEXT, e -> text(e).value(), (e, v) -> text(e).withValue(v)));
        p.add(PropertyDescriptor.font(
                "font", "Font", TEXT, FontSpec.class, e -> text(e).font(), (e, v) -> text(e).withFont(v)));
        p.add(PropertyDescriptor.color("color", "Colour", TEXT, RgbaColor.class, e -> text(e).color(), (e, v) -> {
            TextElement t = text(e);
            return new TextElement(
                    t.properties(),
                    t.value(),
                    t.font(),
                    v,
                    t.align(),
                    t.verticalAlign(),
                    t.lineSpacing(),
                    t.wrap(),
                    t.autoFit(),
                    t.characterSpacing());
        }));
        p.add(PropertyDescriptor.enumProp(
                "align",
                "Align",
                TEXT,
                com.rohit.labelbuilder.model.style.TextAlign.class,
                e -> text(e).align(),
                (e, v) -> {
                    TextElement t = text(e);
                    return new TextElement(
                            t.properties(),
                            t.value(),
                            t.font(),
                            t.color(),
                            v,
                            t.verticalAlign(),
                            t.lineSpacing(),
                            t.wrap(),
                            t.autoFit(),
                            t.characterSpacing());
                }));
        p.add(PropertyDescriptor.enumProp(
                "verticalAlign",
                "Vertical align",
                TEXT,
                com.rohit.labelbuilder.model.style.VerticalAlign.class,
                e -> text(e).verticalAlign(),
                (e, v) -> {
                    TextElement t = text(e);
                    return new TextElement(
                            t.properties(),
                            t.value(),
                            t.font(),
                            t.color(),
                            t.align(),
                            v,
                            t.lineSpacing(),
                            t.wrap(),
                            t.autoFit(),
                            t.characterSpacing());
                }));
        p.add(PropertyDescriptor.decimal("lineSpacing", "Line spacing", TEXT, e -> text(e).lineSpacing(), (e, v) -> {
                    TextElement t = text(e);
                    return new TextElement(
                            t.properties(),
                            t.value(),
                            t.font(),
                            t.color(),
                            t.align(),
                            t.verticalAlign(),
                            v,
                            t.wrap(),
                            t.autoFit(),
                            t.characterSpacing());
                })
                .withRange(0.1, null));
        p.add(PropertyDescriptor.bool("wrap", "Wrap", TEXT, e -> text(e).wrap(), (e, v) -> {
            TextElement t = text(e);
            return new TextElement(
                    t.properties(),
                    t.value(),
                    t.font(),
                    t.color(),
                    t.align(),
                    t.verticalAlign(),
                    t.lineSpacing(),
                    v,
                    t.autoFit(),
                    t.characterSpacing());
        }));
        p.add(PropertyDescriptor.enumProp(
                "autoFit", "Auto-fit", TEXT, AutoFit.class, e -> text(e).autoFit(), (e, v) -> {
                    TextElement t = text(e);
                    return new TextElement(
                            t.properties(),
                            t.value(),
                            t.font(),
                            t.color(),
                            t.align(),
                            t.verticalAlign(),
                            t.lineSpacing(),
                            t.wrap(),
                            v,
                            t.characterSpacing());
                }));
        p.add(PropertyDescriptor.decimal(
                "characterSpacing", "Character spacing", TEXT, e -> text(e).characterSpacing(), (e, v) -> {
                    TextElement t = text(e);
                    return new TextElement(
                            t.properties(),
                            t.value(),
                            t.font(),
                            t.color(),
                            t.align(),
                            t.verticalAlign(),
                            t.lineSpacing(),
                            t.wrap(),
                            t.autoFit(),
                            v);
                }));
        return schema(TextElement.class, p);
    }

    private static ElementSchema rectangleSchema() {
        List<PropertyDescriptor> p = new ArrayList<>();
        p.add(strokeColor(APPEARANCE, e -> rect(e).stroke(), (e, s) -> rect(e).withStroke(s)));
        p.add(strokeWidth(APPEARANCE, e -> rect(e).stroke(), (e, s) -> rect(e).withStroke(s)));
        p.add(dash(APPEARANCE, e -> rect(e).stroke(), (e, s) -> rect(e).withStroke(s)));
        p.add(fillColor(APPEARANCE, e -> rect(e).fill(), (e, f) -> rect(e).withFill(f)));
        p.add(fillEnabled(APPEARANCE, e -> rect(e).fill(), (e, f) -> rect(e).withFill(f)));
        p.add(PropertyDescriptor.decimal(
                        "cornerRadiusMm", "Corner radius (mm)", APPEARANCE, e -> rect(e).cornerRadiusMm(), (e, v) -> {
                            RectangleElement r = rect(e);
                            return new RectangleElement(r.properties(), r.stroke(), r.fill(), v);
                        })
                .withRange(0.0, null));
        return schema(RectangleElement.class, p);
    }

    private static ElementSchema ellipseSchema() {
        List<PropertyDescriptor> p = new ArrayList<>();
        p.add(strokeColor(
                APPEARANCE, e -> ellipse(e).stroke(), (e, s) -> ellipse(e).withStroke(s)));
        p.add(strokeWidth(
                APPEARANCE, e -> ellipse(e).stroke(), (e, s) -> ellipse(e).withStroke(s)));
        p.add(dash(APPEARANCE, e -> ellipse(e).stroke(), (e, s) -> ellipse(e).withStroke(s)));
        p.add(fillColor(APPEARANCE, e -> ellipse(e).fill(), (e, f) -> ellipse(e).withFill(f)));
        p.add(fillEnabled(
                APPEARANCE, e -> ellipse(e).fill(), (e, f) -> ellipse(e).withFill(f)));
        return schema(EllipseElement.class, p);
    }

    private static ElementSchema lineSchema() {
        List<PropertyDescriptor> p = new ArrayList<>();
        p.add(strokeColor(
                APPEARANCE,
                e -> line(e).stroke(),
                (e, s) -> new LineElement(line(e).properties(), s, line(e).lineCap())));
        p.add(strokeWidth(
                APPEARANCE,
                e -> line(e).stroke(),
                (e, s) -> new LineElement(line(e).properties(), s, line(e).lineCap())));
        p.add(dash(
                APPEARANCE,
                e -> line(e).stroke(),
                (e, s) -> new LineElement(line(e).properties(), s, line(e).lineCap())));
        p.add(PropertyDescriptor.enumProp(
                "lineCap",
                "Line cap",
                APPEARANCE,
                LineCap.class,
                e -> line(e).lineCap(),
                (e, v) -> new LineElement(line(e).properties(), line(e).stroke(), v)));
        return schema(LineElement.class, p);
    }

    private static ElementSchema imageSchema() {
        List<PropertyDescriptor> p = new ArrayList<>();
        p.add(PropertyDescriptor.text(
                "assetPath", "Asset path", IMAGE, e -> image(e).assetPath(), (e, v) -> image(e).withAssetPath(v)));
        p.add(PropertyDescriptor.enumProp("fitMode", "Fit", IMAGE, ImageFit.class, e -> image(e).fitMode(), (e, v) -> {
            ImageElement i = image(e);
            return new ImageElement(i.properties(), i.assetPath(), v, i.monochrome(), i.opacity());
        }));
        p.add(PropertyDescriptor.decimal("opacity", "Opacity", IMAGE, e -> image(e).opacity(), (e, v) -> {
                    ImageElement i = image(e);
                    return new ImageElement(i.properties(), i.assetPath(), i.fitMode(), i.monochrome(), v);
                })
                .withRange(0.0, 1.0));
        p.add(PropertyDescriptor.bool(
                "monochromeEnabled",
                "Monochrome",
                IMAGE,
                e -> image(e).monochrome().enabled(),
                (e, v) -> {
                    ImageElement i = image(e);
                    Monochrome m = i.monochrome();
                    return new ImageElement(
                            i.properties(),
                            i.assetPath(),
                            i.fitMode(),
                            new Monochrome(v, m.method(), m.threshold()),
                            i.opacity());
                }));
        p.add(PropertyDescriptor.enumProp(
                "monochromeMethod",
                "Monochrome method",
                IMAGE,
                MonochromeMethod.class,
                e -> image(e).monochrome().method(),
                (e, v) -> {
                    ImageElement i = image(e);
                    Monochrome m = i.monochrome();
                    return new ImageElement(
                            i.properties(),
                            i.assetPath(),
                            i.fitMode(),
                            new Monochrome(m.enabled(), v, m.threshold()),
                            i.opacity());
                }));
        p.add(PropertyDescriptor.integer(
                        "monochromeThreshold",
                        "Threshold",
                        IMAGE,
                        e -> image(e).monochrome().threshold(),
                        (e, v) -> {
                            ImageElement i = image(e);
                            Monochrome m = i.monochrome();
                            return new ImageElement(
                                    i.properties(),
                                    i.assetPath(),
                                    i.fitMode(),
                                    new Monochrome(m.enabled(), m.method(), v),
                                    i.opacity());
                        })
                .withRange(0.0, 255.0));
        return schema(ImageElement.class, p);
    }

    private static ElementSchema barcodeSchema() {
        List<PropertyDescriptor> p = new ArrayList<>();
        p.add(PropertyDescriptor.enumProp(
                "symbology",
                "Symbology",
                BARCODE,
                Symbology.class,
                e -> barcode(e).symbology(),
                (e, v) -> barcode(e).withSymbology(v)));
        p.add(PropertyDescriptor.text("value", "Value", BARCODE, e -> barcode(e).value(), (e, v) -> barcode(e)
                .withValue(v)));
        p.add(PropertyDescriptor.decimal(
                        "xDimensionMm",
                        "X-dimension (mm)",
                        BARCODE,
                        e -> barcode(e).xDimensionMm(),
                        (e, v) -> {
                            BarcodeElement b = barcode(e);
                            return new BarcodeElement(
                                    b.properties(),
                                    b.symbology(),
                                    b.value(),
                                    v,
                                    b.barHeightMm(),
                                    b.quietZoneMm(),
                                    b.checkDigit(),
                                    b.hri(),
                                    b.rotationQuadrant());
                        })
                .withRange(0.0, null));
        p.add(PropertyDescriptor.decimal(
                        "barHeightMm",
                        "Bar height (mm)",
                        BARCODE,
                        e -> barcode(e).barHeightMm(),
                        (e, v) -> {
                            BarcodeElement b = barcode(e);
                            return new BarcodeElement(
                                    b.properties(),
                                    b.symbology(),
                                    b.value(),
                                    b.xDimensionMm(),
                                    v,
                                    b.quietZoneMm(),
                                    b.checkDigit(),
                                    b.hri(),
                                    b.rotationQuadrant());
                        })
                .withRange(0.0, null));
        p.add(PropertyDescriptor.decimal(
                        "quietZoneMm",
                        "Quiet zone (mm)",
                        BARCODE,
                        e -> barcode(e).quietZoneMm(),
                        (e, v) -> {
                            BarcodeElement b = barcode(e);
                            return new BarcodeElement(
                                    b.properties(),
                                    b.symbology(),
                                    b.value(),
                                    b.xDimensionMm(),
                                    b.barHeightMm(),
                                    v,
                                    b.checkDigit(),
                                    b.hri(),
                                    b.rotationQuadrant());
                        })
                .withRange(0.0, null));
        p.add(PropertyDescriptor.enumProp(
                "checkDigit",
                "Check digit",
                BARCODE,
                CheckDigitMode.class,
                e -> barcode(e).checkDigit(),
                (e, v) -> {
                    BarcodeElement b = barcode(e);
                    return new BarcodeElement(
                            b.properties(),
                            b.symbology(),
                            b.value(),
                            b.xDimensionMm(),
                            b.barHeightMm(),
                            b.quietZoneMm(),
                            v,
                            b.hri(),
                            b.rotationQuadrant());
                }));
        p.add(PropertyDescriptor.enumProp(
                "hriPosition",
                "HRI position",
                BARCODE,
                HriPosition.class,
                e -> barcode(e).hri().position(),
                (e, v) -> {
                    BarcodeElement b = barcode(e);
                    Hri h = b.hri();
                    return new BarcodeElement(
                            b.properties(),
                            b.symbology(),
                            b.value(),
                            b.xDimensionMm(),
                            b.barHeightMm(),
                            b.quietZoneMm(),
                            b.checkDigit(),
                            new Hri(v, h.font(), h.showCheckDigit(), h.customText()),
                            b.rotationQuadrant());
                }));
        return schema(BarcodeElement.class, p);
    }

    private static ElementSchema groupSchema() {
        // Groups expose only the common properties; their children are edited on the canvas.
        return schema(GroupElement.class, List.of());
    }

    // --- shared shape helpers (stroke/fill decomposed into inspector-friendly scalars) ---

    private static PropertyDescriptor strokeColor(
            String category,
            java.util.function.Function<LabelElement, Stroke> get,
            java.util.function.BiFunction<LabelElement, Stroke, LabelElement> put) {
        return PropertyDescriptor.color(
                "strokeColor",
                "Stroke colour",
                category,
                RgbaColor.class,
                e -> get.apply(e).color(),
                (e, v) -> put.apply(
                        e, new Stroke(v, get.apply(e).widthMm(), get.apply(e).dash())));
    }

    private static PropertyDescriptor strokeWidth(
            String category,
            java.util.function.Function<LabelElement, Stroke> get,
            java.util.function.BiFunction<LabelElement, Stroke, LabelElement> put) {
        return PropertyDescriptor.decimal(
                        "strokeWidthMm",
                        "Stroke width (mm)",
                        category,
                        e -> get.apply(e).widthMm(),
                        (e, v) -> put.apply(
                                e,
                                new Stroke(get.apply(e).color(), v, get.apply(e).dash())))
                .withRange(0.0, null);
    }

    private static PropertyDescriptor dash(
            String category,
            java.util.function.Function<LabelElement, Stroke> get,
            java.util.function.BiFunction<LabelElement, Stroke, LabelElement> put) {
        return PropertyDescriptor.enumProp(
                "dash",
                "Dash",
                category,
                DashStyle.class,
                e -> get.apply(e).dash(),
                (e, v) -> put.apply(
                        e, new Stroke(get.apply(e).color(), get.apply(e).widthMm(), v)));
    }

    private static PropertyDescriptor fillColor(
            String category,
            java.util.function.Function<LabelElement, Fill> get,
            java.util.function.BiFunction<LabelElement, Fill, LabelElement> put) {
        return PropertyDescriptor.color(
                "fillColor",
                "Fill colour",
                category,
                RgbaColor.class,
                e -> get.apply(e).color(),
                (e, v) -> put.apply(e, get.apply(e).withColor(v)));
    }

    private static PropertyDescriptor fillEnabled(
            String category,
            java.util.function.Function<LabelElement, Fill> get,
            java.util.function.BiFunction<LabelElement, Fill, LabelElement> put) {
        return PropertyDescriptor.bool(
                "fillEnabled",
                "Fill",
                category,
                e -> get.apply(e).enabled(),
                (e, v) -> put.apply(e, get.apply(e).withEnabled(v)));
    }

    // --- casts (safe: the registry only pairs a schema with its element type) ---

    private static TextElement text(LabelElement e) {
        return (TextElement) e;
    }

    private static RectangleElement rect(LabelElement e) {
        return (RectangleElement) e;
    }

    private static EllipseElement ellipse(LabelElement e) {
        return (EllipseElement) e;
    }

    private static LineElement line(LabelElement e) {
        return (LineElement) e;
    }

    private static ImageElement image(LabelElement e) {
        return (ImageElement) e;
    }

    private static BarcodeElement barcode(LabelElement e) {
        return (BarcodeElement) e;
    }
}
