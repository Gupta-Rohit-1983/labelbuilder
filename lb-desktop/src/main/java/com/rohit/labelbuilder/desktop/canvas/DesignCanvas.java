package com.rohit.labelbuilder.desktop.canvas;

import com.rohit.labelbuilder.core.command.AddElementCommand;
import com.rohit.labelbuilder.core.command.Command;
import com.rohit.labelbuilder.core.command.CompositeCommand;
import com.rohit.labelbuilder.core.command.SetBoundsCommand;
import com.rohit.labelbuilder.core.command.SetPropertyCommand;
import com.rohit.labelbuilder.core.edit.ElementFactory;
import com.rohit.labelbuilder.core.edit.ElementKind;
import com.rohit.labelbuilder.desktop.document.DocumentSession;
import com.rohit.labelbuilder.desktop.document.EditActions;
import com.rohit.labelbuilder.model.document.LabelDocument;
import com.rohit.labelbuilder.model.element.LabelElement;
import com.rohit.labelbuilder.model.geom.Bounds;
import com.rohit.labelbuilder.render.scene.SceneMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * The design workspace canvas. Renders the open {@link LabelDocument} through a {@link
 * CanvasViewport} — surface, grid and guides (6a–6b), the elements themselves, and selection with
 * resize/rotate handles (6c) — and hosts the creation tools that place new elements (8a).
 *
 * <p>Elements are painted by flattening the document to a {@code RenderScene} ({@link SceneMapper})
 * and painting that with {@link ScenePainter}; the Java2D reference renderer consumes the very same
 * scene, so the design view and the printout share one description (risk R-03).
 *
 * <p>Every edit goes through {@link DocumentSession#execute}, so everything is undoable. A drag is
 * <b>previewed locally</b> and committed as a single command on release — the history gets one entry
 * per gesture rather than one per mouse-move, and the document is never rebuilt mid-drag.
 *
 * <p>All non-trivial maths lives in pure, unit-tested types ({@link CanvasViewport}, {@link
 * BoundsMm}, {@link SnapEngine}, {@link SelectionModel}); this FX class is the interaction and
 * painting shell around them.
 */
public class DesignCanvas extends Region {

    private static final double WHEEL_ZOOM_STEP = 1.15;
    private static final double BUTTON_ZOOM_STEP = 1.20;
    private static final double FIT_PADDING_PX = 24;
    private static final double MIN_GRID_PX = 4;
    private static final double GUIDE_GRAB_PX = 4;
    private static final double GUIDE_SNAP_PX = 6;
    private static final double HANDLE_PX = 7; // drawn handle square size
    private static final double HANDLE_HIT_PX = 6; // grab radius
    private static final double ROTATE_GAP_PX = 18; // rotate handle distance above the top edge
    private static final double MIN_ITEM_MM = 1;
    private static final double ROTATE_SNAP_DEG = 15;
    private static final double CLICK_SLOP_PX = 3; // below this a creation drag counts as a click

    private enum Mode {
        NONE,
        MOVE,
        RESIZE,
        ROTATE,
        RUBBER_BAND,
        CREATE
    }

    private final DocumentSession session;
    private final EditActions edit;
    private final Canvas canvas = new Canvas();
    private final ObjectProperty<CanvasViewport> viewport = new SimpleObjectProperty<>(CanvasViewport.initial());
    private final ObjectProperty<LabelSurface> surface = new SimpleObjectProperty<>(LabelSurface.defaultSize());
    private final ObjectProperty<GridSettings> grid = new SimpleObjectProperty<>(GridSettings.defaults());
    private final ObservableList<Guide> guides = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<Point2D> pointerMm = new ReadOnlyObjectWrapper<>(null);
    /** The active creation tool; {@code null} means the select/transform tool. */
    private final ObjectProperty<ElementKind> activeTool = new SimpleObjectProperty<>(null);

    private boolean needsInitialFit = true;
    private boolean repaintScheduled;
    private boolean panning;
    private double lastPanX;
    private double lastPanY;
    private int draggedGuide = -1;

    private Mode mode = Mode.NONE;
    private ResizeHandle activeHandle;
    private String activeElementId;
    private BoundsMm startBounds;
    private double pressDeviceX;
    private double pressDeviceY;
    private Point2D pressModel;
    private double rubberStartX;
    private double rubberStartY;
    private double rubberNowX;
    private double rubberNowY;

    /** Live drag preview: element id → its in-progress bounds, uncommitted until release. */
    private final Map<String, BoundsMm> pendingBounds = new LinkedHashMap<>();

    private Double pendingRotationDeg;
    private ContextMenu contextMenu;

    public DesignCanvas(DocumentSession session, EditActions edit) {
        this.session = session;
        this.edit = edit;
        getStyleClass().add("design-canvas");
        canvas.setManaged(false);
        getChildren().add(canvas);
        setFocusTraversable(true);

        viewport.addListener((o, a, b) -> requestRepaint());
        surface.addListener((o, a, b) -> requestRepaint());
        grid.addListener((o, a, b) -> requestRepaint());
        guides.addListener((javafx.collections.ListChangeListener<Guide>) c -> requestRepaint());
        widthProperty().addListener((o, a, b) -> requestRepaint());
        heightProperty().addListener((o, a, b) -> requestRepaint());

        // The canvas is a view of the session: repaint whenever the document or selection changes.
        session.documentProperty().addListener((o, a, b) -> {
            syncSurface();
            requestRepaint();
        });
        session.addSelectionListener(this::requestRepaint);
        syncSurface();

        installNavigation();
    }

    // ---- observable view state ----------------------------------------------------------

    public ReadOnlyObjectProperty<CanvasViewport> viewportProperty() {
        return viewport;
    }

    public ReadOnlyObjectProperty<Point2D> pointerMmProperty() {
        return pointerMm.getReadOnlyProperty();
    }

    public ObjectProperty<LabelSurface> surfaceProperty() {
        return surface;
    }

    public ObjectProperty<GridSettings> gridSettingsProperty() {
        return grid;
    }

    public ObservableList<Guide> guides() {
        return guides;
    }

    /** The active creation tool, or {@code null} for select mode. */
    public ObjectProperty<ElementKind> activeToolProperty() {
        return activeTool;
    }

    /** Arm a creation tool; {@code null} returns to select mode. */
    public void setActiveTool(ElementKind kind) {
        activeTool.set(kind);
    }

    /** Keeps the drawn surface in step with the document's stock. */
    private void syncSurface() {
        LabelDocument document = session.document();
        surface.set(
                new LabelSurface(document.stock().widthMm(), document.stock().heightMm()));
    }

    // ---- snapping -----------------------------------------------------------------------

    public Point2D snap(Point2D modelPoint) {
        return new Point2D(snapX(modelPoint.getX()), snapY(modelPoint.getY()));
    }

    private double snapX(double xMm) {
        return SnapEngine.snap(xMm, grid.get(), guidePositions(Orientation.VERTICAL), thresholdMm());
    }

    private double snapY(double yMm) {
        return SnapEngine.snap(yMm, grid.get(), guidePositions(Orientation.HORIZONTAL), thresholdMm());
    }

    private double thresholdMm() {
        return GUIDE_SNAP_PX / viewport.get().scale();
    }

    private double[] guidePositions(Orientation orientation) {
        return guides.stream()
                .filter(g -> g.orientation() == orientation)
                .mapToDouble(Guide::positionMm)
                .toArray();
    }

    // ---- zoom commands ------------------------------------------------------------------

    public void zoomIn() {
        zoomAtCentre(BUTTON_ZOOM_STEP);
    }

    public void zoomOut() {
        zoomAtCentre(1 / BUTTON_ZOOM_STEP);
    }

    public void zoomToFit() {
        LabelSurface s = surface.get();
        viewport.set(viewport.get().fit(s.widthMm(), s.heightMm(), getWidth(), getHeight(), FIT_PADDING_PX));
    }

    private void zoomAtCentre(double factor) {
        viewport.set(viewport.get().zoomedByAt(factor, getWidth() / 2, getHeight() / 2));
    }

    // ---- document view ------------------------------------------------------------------

    /**
     * The document as it should currently be seen: the committed document plus any in-progress drag
     * preview. Painting and hit-testing both use this so the drag feels direct while the history
     * stays clean.
     */
    private LabelDocument effectiveDocument() {
        LabelDocument document = session.document();
        if (pendingBounds.isEmpty() && pendingRotationDeg == null) {
            return document;
        }
        LabelDocument preview = document;
        for (Map.Entry<String, BoundsMm> entry : pendingBounds.entrySet()) {
            LabelElement element = preview.findElement(entry.getKey()).orElse(null);
            if (element != null) {
                preview = preview.replaceElement(element.withBounds(toModel(entry.getValue())));
            }
        }
        if (pendingRotationDeg != null && activeElementId != null) {
            LabelElement element = preview.findElement(activeElementId).orElse(null);
            if (element != null) {
                preview = preview.replaceElement(element.withRotationDeg(pendingRotationDeg));
            }
        }
        return preview;
    }

    private static Bounds toModel(BoundsMm b) {
        return new Bounds(b.x(), b.y(), b.w(), b.h());
    }

    private static BoundsMm toView(Bounds b) {
        return new BoundsMm(b.xMm(), b.yMm(), b.widthMm(), b.heightMm());
    }

    private BoundsMm boundsOf(LabelElement element) {
        BoundsMm pending = pendingBounds.get(element.id());
        return pending != null ? pending : toView(element.bounds());
    }

    private double rotationOf(LabelElement element) {
        return pendingRotationDeg != null && element.id().equals(activeElementId)
                ? pendingRotationDeg
                : element.rotationDeg();
    }

    private Point2D centreOf(LabelElement element) {
        return boundsOf(element).center();
    }

    /** Elements the user may interact with — locked ones are inert. */
    private List<LabelElement> selectableElements() {
        List<LabelElement> out = new ArrayList<>();
        for (LabelElement element : session.document().elements()) {
            if (!element.locked() && element.visible()) {
                out.add(element);
            }
        }
        return out;
    }

    // ---- input --------------------------------------------------------------------------

    private void installNavigation() {
        setOnScroll(e -> {
            double factor = e.getDeltaY() >= 0 ? WHEEL_ZOOM_STEP : 1 / WHEEL_ZOOM_STEP;
            viewport.set(viewport.get().zoomedByAt(factor, e.getX(), e.getY()));
            e.consume();
        });
        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(this::onReleased);
        setOnMouseClicked(this::onClicked);
        setOnMouseMoved(e -> updatePointer(e.getX(), e.getY()));
        setOnMouseExited(e -> pointerMm.set(null));
        setOnKeyPressed(this::onKeyPressed);
        setOnContextMenuRequested(this::onContextMenuRequested);
    }

    /**
     * The right-click menu, supplied by the shell so the canvas needs no knowledge of action ids.
     * Built once; its items track their actions' enablement.
     */
    public void setElementContextMenu(ContextMenu menu) {
        this.contextMenu = menu;
    }

    /**
     * Right-click selects what is under the pointer before opening the menu — otherwise the menu
     * would act on a selection the user cannot see, which is the classic way to delete the wrong
     * thing. Right-clicking empty space clears the selection.
     */
    private void onContextMenuRequested(ContextMenuEvent e) {
        if (contextMenu == null) {
            return;
        }
        CanvasViewport vp = viewport.get();
        LabelElement hit = topElementAt(new Point2D(vp.toModelX(e.getX()), vp.toModelY(e.getY())));
        if (hit == null) {
            session.clearSelection();
        } else if (!session.isSelected(hit.id())) {
            session.select(hit.id());
        }
        contextMenu.show(this, e.getScreenX(), e.getScreenY());
        e.consume();
    }

    /**
     * Canvas-scoped keys. Arrow keys nudge the selection by one grid step, or ten with Shift; Delete
     * removes it; Ctrl+X/C/V act on elements.
     *
     * <p>These are bound here rather than as global accelerators so they only apply while the canvas
     * has focus — the arrows and the clipboard keys keep their normal meaning inside any text field
     * (a scene-wide Ctrl+C would otherwise shadow {@code TextInputControl}'s own handling).
     */
    private void onKeyPressed(KeyEvent e) {
        if (e.isShortcutDown()) {
            switch (e.getCode()) {
                case X -> edit.cut();
                case C -> edit.copy();
                case V -> edit.paste();
                default -> {
                    return;
                }
            }
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
            edit.delete();
            e.consume();
            return;
        }
        double step = grid.get().spacingMm() * (e.isShiftDown() ? 10 : 1);
        double dx = 0;
        double dy = 0;
        switch (e.getCode()) {
            case LEFT -> dx = -step;
            case RIGHT -> dx = step;
            case UP -> dy = -step;
            case DOWN -> dy = step;
            default -> {
                return;
            }
        }
        edit.nudge(dx, dy);
        e.consume();
    }

    private void onPressed(MouseEvent e) {
        requestFocus();
        pressDeviceX = e.getX();
        pressDeviceY = e.getY();
        pressModel = model(e);

        if (e.getButton() == MouseButton.MIDDLE) {
            panning = true;
            lastPanX = e.getX();
            lastPanY = e.getY();
            e.consume();
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }

        // 0) a creation tool is armed: drag out the new element's box
        if (activeTool.get() != null) {
            mode = Mode.CREATE;
            rubberStartX = e.getX();
            rubberStartY = e.getY();
            rubberNowX = e.getX();
            rubberNowY = e.getY();
            e.consume();
            return;
        }
        // 1) grab a guide line
        draggedGuide = guideAt(e.getX(), e.getY());
        if (draggedGuide >= 0) {
            mode = Mode.NONE;
            e.consume();
            return;
        }
        // 2) grab a resize/rotate handle of the single selected element
        LabelElement single = singleSelected();
        if (single != null) {
            if (rotateHandleAt(single, e.getX(), e.getY())) {
                mode = Mode.ROTATE;
                activeElementId = single.id();
                e.consume();
                return;
            }
            ResizeHandle handle = handleAt(single, e.getX(), e.getY());
            if (handle != null) {
                mode = Mode.RESIZE;
                activeElementId = single.id();
                activeHandle = handle;
                startBounds = boundsOf(single);
                e.consume();
                return;
            }
        }
        // 3) select / move an element, or start a rubber-band
        LabelElement hit = topElementAt(pressModel);
        if (hit != null) {
            if (e.isShiftDown()) {
                session.toggleSelection(hit.id());
            } else if (!session.isSelected(hit.id())) {
                session.select(hit.id());
            }
            beginMove();
        } else {
            if (!e.isShiftDown()) {
                session.clearSelection();
            }
            mode = Mode.RUBBER_BAND;
            rubberStartX = e.getX();
            rubberStartY = e.getY();
            rubberNowX = e.getX();
            rubberNowY = e.getY();
        }
        e.consume();
        draw();
    }

    private void beginMove() {
        mode = Mode.MOVE;
        pendingBounds.clear();
        for (LabelElement element : session.selectedElements()) {
            if (!element.locked()) {
                pendingBounds.put(element.id(), toView(element.bounds()));
            }
        }
    }

    private void onDragged(MouseEvent e) {
        if (panning) {
            viewport.set(viewport.get().pannedBy(e.getX() - lastPanX, e.getY() - lastPanY));
            lastPanX = e.getX();
            lastPanY = e.getY();
            e.consume();
        } else if (draggedGuide >= 0) {
            Guide g = guides.get(draggedGuide);
            double pos = g.orientation() == Orientation.VERTICAL
                    ? snapX(viewport.get().toModelX(e.getX()))
                    : snapY(viewport.get().toModelY(e.getY()));
            guides.set(draggedGuide, g.movedTo(pos));
            e.consume();
        } else {
            switch (mode) {
                case MOVE -> dragMove(e);
                case RESIZE -> dragResize(e);
                case ROTATE -> dragRotate(e);
                case RUBBER_BAND, CREATE -> {
                    rubberNowX = e.getX();
                    rubberNowY = e.getY();
                    draw();
                }
                default -> {}
            }
        }
        updatePointer(e.getX(), e.getY());
    }

    private void dragMove(MouseEvent e) {
        String primaryId = session.primarySelectedId();
        BoundsMm ps = primaryId == null ? null : pendingStart(primaryId);
        if (ps == null) {
            return;
        }
        Point2D cur = model(e);
        double rawX = ps.x() + (cur.getX() - pressModel.getX());
        double rawY = ps.y() + (cur.getY() - pressModel.getY());
        double effDx = snapX(rawX) - ps.x();
        double effDy = snapY(rawY) - ps.y();
        for (LabelElement element : session.selectedElements()) {
            BoundsMm start = pendingStart(element.id());
            if (start != null) {
                pendingBounds.put(element.id(), start.translated(effDx, effDy));
            }
        }
        draw();
    }

    /** The element's bounds as of the gesture start (the committed document). */
    private BoundsMm pendingStart(String elementId) {
        return session.document()
                .findElement(elementId)
                .map(e -> toView(e.bounds()))
                .orElse(null);
    }

    private void dragResize(MouseEvent e) {
        LabelElement element = session.document().findElement(activeElementId).orElse(null);
        if (element == null) {
            return;
        }
        double scale = viewport.get().scale();
        double worldDx = (e.getX() - pressDeviceX) / scale;
        double worldDy = (e.getY() - pressDeviceY) / scale;
        // Project the device drag into the element's local (unrotated) axes.
        double r = Math.toRadians(-element.rotationDeg());
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        double localDx = worldDx * cos - worldDy * sin;
        double localDy = worldDx * sin + worldDy * cos;
        pendingBounds.put(activeElementId, startBounds.resized(activeHandle, localDx, localDy, MIN_ITEM_MM));
        draw();
    }

    private void dragRotate(MouseEvent e) {
        LabelElement element = session.document().findElement(activeElementId).orElse(null);
        if (element == null) {
            return;
        }
        Point2D c = toView(element.bounds()).center();
        Point2D m = model(e);
        double angle = Math.toDegrees(Math.atan2(m.getY() - c.getY(), m.getX() - c.getX())) + 90;
        if (e.isShiftDown()) {
            angle = Math.round(angle / ROTATE_SNAP_DEG) * ROTATE_SNAP_DEG;
        }
        pendingRotationDeg = angle;
        draw();
    }

    private void onReleased(MouseEvent e) {
        if (e.getButton() == MouseButton.MIDDLE) {
            panning = false;
            e.consume();
            return;
        }
        switch (mode) {
            case RUBBER_BAND -> selectWithinRubberBand();
            case CREATE -> commitCreation(e);
            case MOVE, RESIZE -> commitTransform(mode == Mode.MOVE ? "Move" : "Resize");
            case ROTATE -> commitRotation();
            default -> {}
        }
        mode = Mode.NONE;
        activeElementId = null;
        activeHandle = null;
        draggedGuide = -1;
        pendingBounds.clear();
        pendingRotationDeg = null;
        draw();
    }

    /** Commit a completed move/resize as one undoable command covering every dragged element. */
    private void commitTransform(String label) {
        List<Command> edits = new ArrayList<>();
        for (Map.Entry<String, BoundsMm> entry : pendingBounds.entrySet()) {
            Bounds target = toModel(entry.getValue());
            session.document().findElement(entry.getKey()).ifPresent(element -> {
                if (!element.bounds().equals(target)) {
                    edits.add(new SetBoundsCommand(element.id(), target, label));
                }
            });
        }
        if (edits.isEmpty()) {
            return; // a click, or a drag that ended where it started
        }
        session.execute(edits.size() == 1 ? edits.getFirst() : new CompositeCommand(label, edits));
    }

    private void commitRotation() {
        if (pendingRotationDeg == null || activeElementId == null) {
            return;
        }
        session.execute(new SetPropertyCommand(activeElementId, "rotation", pendingRotationDeg));
    }

    /** Turn the dragged-out box (or a bare click) into a new element, then return to select mode. */
    private void commitCreation(MouseEvent e) {
        ElementKind kind = activeTool.get();
        if (kind == null) {
            return;
        }
        String id = ElementFactory.newId();
        String layerId = session.activeLayerId();
        boolean dragged = Math.hypot(e.getX() - rubberStartX, e.getY() - rubberStartY) > CLICK_SLOP_PX;

        LabelElement element;
        if (dragged) {
            CanvasViewport vp = viewport.get();
            double x1 = snapX(Math.min(vp.toModelX(rubberStartX), vp.toModelX(e.getX())));
            double y1 = snapY(Math.min(vp.toModelY(rubberStartY), vp.toModelY(e.getY())));
            double x2 = snapX(Math.max(vp.toModelX(rubberStartX), vp.toModelX(e.getX())));
            double y2 = snapY(Math.max(vp.toModelY(rubberStartY), vp.toModelY(e.getY())));
            // A line is defined by the drag's corners; other kinds get a positive-area box.
            double w = kind == ElementKind.LINE ? x2 - x1 : Math.max(MIN_ITEM_MM, x2 - x1);
            double h = kind == ElementKind.LINE ? y2 - y1 : Math.max(MIN_ITEM_MM, y2 - y1);
            element = ElementFactory.create(kind, id, layerId, new Bounds(x1, y1, w, h));
        } else {
            element =
                    ElementFactory.createDefault(kind, id, layerId, snapX(pressModel.getX()), snapY(pressModel.getY()));
        }

        session.execute(new AddElementCommand(element));
        session.select(element.id());
        activeTool.set(null); // one placement per arming, like every other designer
    }

    private void onClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            int hit = guideAt(e.getX(), e.getY());
            if (hit >= 0) {
                guides.remove(hit);
                e.consume();
            }
        }
    }

    private void selectWithinRubberBand() {
        CanvasViewport vp = viewport.get();
        double x1 = Math.min(vp.toModelX(rubberStartX), vp.toModelX(rubberNowX));
        double y1 = Math.min(vp.toModelY(rubberStartY), vp.toModelY(rubberNowY));
        double x2 = Math.max(vp.toModelX(rubberStartX), vp.toModelX(rubberNowX));
        double y2 = Math.max(vp.toModelY(rubberStartY), vp.toModelY(rubberNowY));
        BoundsMm rubber = new BoundsMm(x1, y1, x2 - x1, y2 - y1);
        List<String> hits = selectableElements().stream()
                .filter(element -> toView(element.bounds()).intersects(rubber))
                .map(LabelElement::id)
                .toList();
        session.addToSelection(hits);
    }

    // ---- hit-testing --------------------------------------------------------------------

    private Point2D model(MouseEvent e) {
        CanvasViewport vp = viewport.get();
        return new Point2D(vp.toModelX(e.getX()), vp.toModelY(e.getY()));
    }

    private LabelElement singleSelected() {
        if (session.selectionSize() != 1) {
            return null;
        }
        return session.document().findElement(session.primarySelectedId()).orElse(null);
    }

    private LabelElement topElementAt(Point2D modelPoint) {
        List<LabelElement> candidates = selectableElements();
        for (int i = candidates.size() - 1; i >= 0; i--) { // last drawn is on top
            LabelElement element = candidates.get(i);
            Point2D local = rotateAbout(modelPoint, centreOf(element), -rotationOf(element));
            if (boundsOf(element).contains(local.getX(), local.getY())) {
                return element;
            }
        }
        return null;
    }

    private ResizeHandle handleAt(LabelElement element, double deviceX, double deviceY) {
        for (ResizeHandle handle : ResizeHandle.values()) {
            Point2D d = deviceHandlePoint(element, handle);
            if (Math.hypot(deviceX - d.getX(), deviceY - d.getY()) <= HANDLE_HIT_PX) {
                return handle;
            }
        }
        return null;
    }

    private boolean rotateHandleAt(LabelElement element, double deviceX, double deviceY) {
        Point2D d = deviceRotateHandle(element);
        return Math.hypot(deviceX - d.getX(), deviceY - d.getY()) <= HANDLE_HIT_PX;
    }

    private int guideAt(double deviceX, double deviceY) {
        CanvasViewport vp = viewport.get();
        for (int i = 0; i < guides.size(); i++) {
            Guide g = guides.get(i);
            double distance = g.orientation() == Orientation.VERTICAL
                    ? Math.abs(deviceX - vp.toDeviceX(g.positionMm()))
                    : Math.abs(deviceY - vp.toDeviceY(g.positionMm()));
            if (distance <= GUIDE_GRAB_PX) {
                return i;
            }
        }
        return -1;
    }

    private Point2D deviceHandlePoint(LabelElement element, ResizeHandle handle) {
        Point2D local = boundsOf(element).handlePoint(handle);
        Point2D world = rotateAbout(local, centreOf(element), rotationOf(element));
        CanvasViewport vp = viewport.get();
        return new Point2D(vp.toDeviceX(world.getX()), vp.toDeviceY(world.getY()));
    }

    private Point2D deviceRotateHandle(LabelElement element) {
        Point2D n = deviceHandlePoint(element, ResizeHandle.N);
        return new Point2D(n.getX(), n.getY() - ROTATE_GAP_PX);
    }

    void addGuide(Orientation orientation, double positionMm) {
        double snapped = orientation == Orientation.VERTICAL ? snapX(positionMm) : snapY(positionMm);
        guides.add(new Guide(orientation, snapped));
    }

    private void updatePointer(double deviceX, double deviceY) {
        CanvasViewport vp = viewport.get();
        pointerMm.set(new Point2D(vp.toModelX(deviceX), vp.toModelY(deviceY)));
    }

    private static Point2D rotateAbout(Point2D p, Point2D c, double deg) {
        double r = Math.toRadians(deg);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        double dx = p.getX() - c.getX();
        double dy = p.getY() - c.getY();
        return new Point2D(c.getX() + dx * cos - dy * sin, c.getY() + dx * sin + dy * cos);
    }

    // ---- rendering ----------------------------------------------------------------------

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        if (needsInitialFit && getWidth() > 0 && getHeight() > 0) {
            needsInitialFit = false;
            zoomToFit();
        }
        draw();
    }

    /**
     * Coalesces redraw requests to at most one paint per pulse (Phase 6e): several property
     * changes in one gesture (e.g. viewport + selection) collapse into a single {@link #draw()}
     * instead of repainting once per change.
     */
    private void requestRepaint() {
        if (repaintScheduled) {
            return;
        }
        repaintScheduled = true;
        Platform.runLater(() -> {
            repaintScheduled = false;
            draw();
        });
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setFill(Color.web("#dfe1e5"));
        g.fillRect(0, 0, w, h);

        CanvasViewport vp = viewport.get();
        LabelSurface s = surface.get();
        double scale = vp.scale();
        double x = vp.toDeviceX(0);
        double y = vp.toDeviceY(0);
        double sw = s.widthMm() * scale;
        double sh = s.heightMm() * scale;

        g.setFill(Color.rgb(0, 0, 0, 0.13));
        g.fillRect(x + 3, y + 3, sw, sh);
        g.setFill(Color.WHITE);
        g.fillRect(x, y, sw, sh);

        BoundsMm visible = vp.visibleModelBounds(w, h);
        drawGrid(g, vp, s, visible);

        g.setStroke(Color.web("#9aa0a6"));
        g.setLineWidth(1);
        g.strokeRect(x - 0.5, y - 0.5, sw + 1, sh + 1);

        // The elements, through the shared RenderScene seam (culled by ScenePainter).
        ScenePainter.paint(g, vp, SceneMapper.toScene(effectiveDocument()), visible);

        drawSelection(g);
        drawGuides(g, vp, w, h);
        drawRubberBand(g);
    }

    private void drawSelection(GraphicsContext g) {
        g.setStroke(Color.web("#1a73e8"));
        g.setLineWidth(1.5);
        LabelDocument document = session.document();
        for (String id : session.selectedIds()) {
            document.findElement(id).ifPresent(element -> outline(g, element));
        }
        LabelElement primary = singleSelected();
        if (primary != null) {
            for (ResizeHandle handle : ResizeHandle.values()) {
                handleSquare(g, deviceHandlePoint(primary, handle));
            }
            Point2D n = deviceHandlePoint(primary, ResizeHandle.N);
            Point2D rot = deviceRotateHandle(primary);
            g.setStroke(Color.web("#1a73e8"));
            g.strokeLine(n.getX(), n.getY(), rot.getX(), rot.getY());
            g.setFill(Color.WHITE);
            g.fillOval(rot.getX() - HANDLE_PX / 2, rot.getY() - HANDLE_PX / 2, HANDLE_PX, HANDLE_PX);
            g.strokeOval(rot.getX() - HANDLE_PX / 2, rot.getY() - HANDLE_PX / 2, HANDLE_PX, HANDLE_PX);
        }
    }

    private void outline(GraphicsContext g, LabelElement element) {
        CanvasViewport vp = viewport.get();
        BoundsMm b = boundsOf(element);
        double scale = vp.scale();
        Point2D c = b.center();
        g.save();
        g.translate(vp.toDeviceX(c.getX()), vp.toDeviceY(c.getY()));
        g.rotate(rotationOf(element));
        double dw = b.w() * scale;
        double dh = b.h() * scale;
        g.strokeRect(-dw / 2, -dh / 2, dw, dh);
        g.restore();
    }

    private void handleSquare(GraphicsContext g, Point2D d) {
        g.setFill(Color.WHITE);
        g.setStroke(Color.web("#1a73e8"));
        g.setLineWidth(1);
        g.fillRect(d.getX() - HANDLE_PX / 2, d.getY() - HANDLE_PX / 2, HANDLE_PX, HANDLE_PX);
        g.strokeRect(d.getX() - HANDLE_PX / 2, d.getY() - HANDLE_PX / 2, HANDLE_PX, HANDLE_PX);
    }

    private void drawGrid(GraphicsContext g, CanvasViewport vp, LabelSurface s, BoundsMm visible) {
        GridSettings settings = grid.get();
        if (!settings.showGrid()) {
            return;
        }
        double step = settings.spacingMm();
        if (step * vp.scale() < MIN_GRID_PX) {
            return; // too dense to be useful — would smear into a solid block
        }
        g.setStroke(Color.web("#e3e6ea"));
        g.setLineWidth(1);

        // Only iterate the grid lines within the visible ∩ surface region (Phase 6e) — avoids
        // looping the whole surface when zoomed into a corner.
        double xFrom = Math.max(0, Math.floor(Math.max(0, visible.x()) / step) * step);
        double xTo = Math.min(s.widthMm(), visible.right());
        double yFrom = Math.max(0, Math.floor(Math.max(0, visible.y()) / step) * step);
        double yTo = Math.min(s.heightMm(), visible.bottom());
        double top = vp.toDeviceY(Math.max(0, Math.min(visible.y(), s.heightMm())));
        double bottom = vp.toDeviceY(yTo);
        double left = vp.toDeviceX(Math.max(0, Math.min(visible.x(), s.widthMm())));
        double right = vp.toDeviceX(xTo);

        for (double mm = xFrom; mm <= xTo + 1e-6; mm += step) {
            double px = Math.round(vp.toDeviceX(mm)) + 0.5;
            g.strokeLine(px, top, px, bottom);
        }
        for (double mm = yFrom; mm <= yTo + 1e-6; mm += step) {
            double py = Math.round(vp.toDeviceY(mm)) + 0.5;
            g.strokeLine(left, py, right, py);
        }
    }

    private void drawGuides(GraphicsContext g, CanvasViewport vp, double w, double h) {
        g.setStroke(Color.web("#12b5cb"));
        g.setLineWidth(1);
        for (Guide guide : guides) {
            if (guide.orientation() == Orientation.VERTICAL) {
                double px = Math.round(vp.toDeviceX(guide.positionMm())) + 0.5;
                g.strokeLine(px, 0, px, h);
            } else {
                double py = Math.round(vp.toDeviceY(guide.positionMm())) + 0.5;
                g.strokeLine(0, py, w, py);
            }
        }
    }

    /** The rubber-band selection box, and the same visual while dragging out a new element. */
    private void drawRubberBand(GraphicsContext g) {
        if (mode != Mode.RUBBER_BAND && mode != Mode.CREATE) {
            return;
        }
        double rx = Math.min(rubberStartX, rubberNowX);
        double ry = Math.min(rubberStartY, rubberNowY);
        double rw = Math.abs(rubberNowX - rubberStartX);
        double rh = Math.abs(rubberNowY - rubberStartY);
        g.setFill(Color.rgb(26, 115, 232, 0.10));
        g.fillRect(rx, ry, rw, rh);
        g.setStroke(Color.web("#1a73e8"));
        g.setLineWidth(1);
        g.setLineDashes(4, 3);
        g.strokeRect(rx + 0.5, ry + 0.5, rw, rh);
        g.setLineDashes(null);
    }
}
