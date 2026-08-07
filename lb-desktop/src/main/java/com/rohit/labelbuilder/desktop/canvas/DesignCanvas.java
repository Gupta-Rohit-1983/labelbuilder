package com.rohit.labelbuilder.desktop.canvas;

import java.util.HashMap;
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
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * The design workspace canvas (Phase 6a–6c): renders the label surface through a {@link
 * CanvasViewport}, the grid and alignment guides (6b), and the placeholder {@link CanvasItem}s
 * with selection, resize and rotate handles (6c).
 *
 * <p>Sits in the docking Center slot (framed with rulers by {@link CanvasView}). It reports the
 * pointer's model-mm position and the current zoom as observable properties for the status bar.
 *
 * <p>All non-trivial maths lives in pure, unit-tested types ({@link CanvasViewport}, {@link
 * BoundsMm}, {@link SnapEngine}, {@link SelectionModel}); this FX class is the interaction and
 * painting shell around them. The items are placeholders until Phase 7's real element model.
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

    private enum Mode {
        NONE,
        MOVE,
        RESIZE,
        ROTATE,
        RUBBER_BAND
    }

    private final Canvas canvas = new Canvas();
    private final ObjectProperty<CanvasViewport> viewport = new SimpleObjectProperty<>(CanvasViewport.initial());
    private final ObjectProperty<LabelSurface> surface = new SimpleObjectProperty<>(LabelSurface.defaultSize());
    private final ObjectProperty<GridSettings> grid = new SimpleObjectProperty<>(GridSettings.defaults());
    private final ObservableList<Guide> guides = FXCollections.observableArrayList();
    private final ObservableList<CanvasItem> items = FXCollections.observableArrayList();
    private final SelectionModel<CanvasItem> selection = new SelectionModel<>();
    private final ReadOnlyObjectWrapper<Point2D> pointerMm = new ReadOnlyObjectWrapper<>(null);

    private boolean needsInitialFit = true;
    private boolean repaintScheduled;
    private boolean panning;
    private double lastPanX;
    private double lastPanY;
    private int draggedGuide = -1;

    private Mode mode = Mode.NONE;
    private ResizeHandle activeHandle;
    private CanvasItem activeItem;
    private BoundsMm startBounds;
    private double pressDeviceX;
    private double pressDeviceY;
    private Point2D pressModel;
    private final Map<CanvasItem, BoundsMm> moveStart = new HashMap<>();
    private double rubberStartX;
    private double rubberStartY;
    private double rubberNowX;
    private double rubberNowY;

    public DesignCanvas() {
        getStyleClass().add("design-canvas");
        canvas.setManaged(false);
        getChildren().add(canvas);
        setFocusTraversable(true);

        viewport.addListener((o, a, b) -> requestRepaint());
        surface.addListener((o, a, b) -> requestRepaint());
        grid.addListener((o, a, b) -> requestRepaint());
        guides.addListener((javafx.collections.ListChangeListener<Guide>) c -> requestRepaint());
        items.addListener((javafx.collections.ListChangeListener<CanvasItem>) c -> requestRepaint());
        widthProperty().addListener((o, a, b) -> requestRepaint());
        heightProperty().addListener((o, a, b) -> requestRepaint());

        // Placeholder objects so selection/handles are demonstrable before Phase 7's element model.
        items.add(new CanvasItem("Text", new BoundsMm(14, 12, 42, 14)));
        items.add(new CanvasItem("Box", new BoundsMm(22, 34, 30, 16)));

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

    public ObservableList<CanvasItem> items() {
        return items;
    }

    // ---- snapping (used by moves and, later, element drags in Phase 8) ------------------

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

        // 1) grab a guide line
        draggedGuide = guideAt(e.getX(), e.getY());
        if (draggedGuide >= 0) {
            mode = Mode.NONE;
            e.consume();
            return;
        }
        // 2) grab a resize/rotate handle of the single selected item
        CanvasItem single = selection.size() == 1 ? selection.primary() : null;
        if (single != null) {
            if (rotateHandleAt(single, e.getX(), e.getY())) {
                mode = Mode.ROTATE;
                activeItem = single;
                e.consume();
                return;
            }
            ResizeHandle handle = handleAt(single, e.getX(), e.getY());
            if (handle != null) {
                mode = Mode.RESIZE;
                activeItem = single;
                activeHandle = handle;
                startBounds = single.bounds();
                e.consume();
                return;
            }
        }
        // 3) select / move an item, or start a rubber-band
        CanvasItem hit = topItemAt(pressModel);
        if (hit != null) {
            if (e.isShiftDown()) {
                selection.toggle(hit);
            } else if (!selection.isSelected(hit)) {
                selection.replaceWith(hit);
            }
            beginMove();
        } else {
            if (!e.isShiftDown()) {
                selection.clear();
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
        moveStart.clear();
        for (CanvasItem item : selection.selected()) {
            moveStart.put(item, item.bounds());
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
                case RUBBER_BAND -> {
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
        CanvasItem primary = selection.primary();
        BoundsMm ps = moveStart.get(primary);
        if (ps == null) {
            return;
        }
        Point2D cur = model(e);
        double rawX = ps.x() + (cur.getX() - pressModel.getX());
        double rawY = ps.y() + (cur.getY() - pressModel.getY());
        double effDx = snapX(rawX) - ps.x();
        double effDy = snapY(rawY) - ps.y();
        for (CanvasItem item : selection.selected()) {
            item.setBounds(moveStart.get(item).translated(effDx, effDy));
        }
        draw();
    }

    private void dragResize(MouseEvent e) {
        double scale = viewport.get().scale();
        double worldDx = (e.getX() - pressDeviceX) / scale;
        double worldDy = (e.getY() - pressDeviceY) / scale;
        // Project the device drag into the item's local (unrotated) axes.
        double r = Math.toRadians(-activeItem.rotationDeg());
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        double localDx = worldDx * cos - worldDy * sin;
        double localDy = worldDx * sin + worldDy * cos;
        activeItem.setBounds(startBounds.resized(activeHandle, localDx, localDy, MIN_ITEM_MM));
        draw();
    }

    private void dragRotate(MouseEvent e) {
        Point2D c = activeItem.center();
        Point2D m = model(e);
        double angle = Math.toDegrees(Math.atan2(m.getY() - c.getY(), m.getX() - c.getX())) + 90;
        if (e.isShiftDown()) {
            angle = Math.round(angle / ROTATE_SNAP_DEG) * ROTATE_SNAP_DEG;
        }
        activeItem.setRotationDeg(angle);
        draw();
    }

    private void onReleased(MouseEvent e) {
        if (e.getButton() == MouseButton.MIDDLE) {
            panning = false;
            e.consume();
            return;
        }
        if (mode == Mode.RUBBER_BAND) {
            selectWithinRubberBand();
        }
        mode = Mode.NONE;
        activeItem = null;
        activeHandle = null;
        draggedGuide = -1;
        draw();
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
        selection.addAll(
                items.stream().filter(item -> item.bounds().intersects(rubber)).toList());
    }

    // ---- hit-testing --------------------------------------------------------------------

    private Point2D model(MouseEvent e) {
        CanvasViewport vp = viewport.get();
        return new Point2D(vp.toModelX(e.getX()), vp.toModelY(e.getY()));
    }

    private CanvasItem topItemAt(Point2D modelPoint) {
        for (int i = items.size() - 1; i >= 0; i--) { // last drawn is on top
            CanvasItem item = items.get(i);
            Point2D local = rotateAbout(modelPoint, item.center(), -item.rotationDeg());
            if (item.bounds().contains(local.getX(), local.getY())) {
                return item;
            }
        }
        return null;
    }

    private ResizeHandle handleAt(CanvasItem item, double deviceX, double deviceY) {
        for (ResizeHandle handle : ResizeHandle.values()) {
            Point2D d = deviceHandlePoint(item, handle);
            if (Math.hypot(deviceX - d.getX(), deviceY - d.getY()) <= HANDLE_HIT_PX) {
                return handle;
            }
        }
        return null;
    }

    private boolean rotateHandleAt(CanvasItem item, double deviceX, double deviceY) {
        Point2D d = deviceRotateHandle(item);
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

    private Point2D deviceHandlePoint(CanvasItem item, ResizeHandle handle) {
        Point2D local = item.bounds().handlePoint(handle);
        Point2D world = rotateAbout(local, item.center(), item.rotationDeg());
        CanvasViewport vp = viewport.get();
        return new Point2D(vp.toDeviceX(world.getX()), vp.toDeviceY(world.getY()));
    }

    private Point2D deviceRotateHandle(CanvasItem item) {
        Point2D n = deviceHandlePoint(item, ResizeHandle.N);
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

        // Cull items whose rotated bounding box lies entirely outside the viewport (Phase 6e).
        for (CanvasItem item : items) {
            if (visible.intersects(item.bounds().rotatedAabb(item.rotationDeg()))) {
                drawItem(g, vp, item);
            }
        }
        drawSelection(g, vp);
        drawGuides(g, vp, w, h);
        drawRubberBand(g);
    }

    private void drawItem(GraphicsContext g, CanvasViewport vp, CanvasItem item) {
        BoundsMm b = item.bounds();
        double scale = vp.scale();
        Point2D c = item.center();
        g.save();
        g.translate(vp.toDeviceX(c.getX()), vp.toDeviceY(c.getY()));
        g.rotate(item.rotationDeg());
        double dw = b.w() * scale;
        double dh = b.h() * scale;
        g.setFill(Color.web("#eef2f7"));
        g.fillRect(-dw / 2, -dh / 2, dw, dh);
        g.setStroke(Color.web("#7a8290"));
        g.setLineWidth(1);
        g.strokeRect(-dw / 2, -dh / 2, dw, dh);
        g.setFill(Color.web("#5a6472"));
        g.fillText(item.label(), -dw / 2 + 4, -dh / 2 + 14);
        g.restore();
    }

    private void drawSelection(GraphicsContext g, CanvasViewport vp) {
        g.setStroke(Color.web("#1a73e8"));
        g.setLineWidth(1.5);
        for (CanvasItem item : selection.selected()) {
            outline(g, vp, item);
        }
        CanvasItem primary = selection.size() == 1 ? selection.primary() : null;
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

    private void outline(GraphicsContext g, CanvasViewport vp, CanvasItem item) {
        BoundsMm b = item.bounds();
        double scale = vp.scale();
        Point2D c = item.center();
        g.save();
        g.translate(vp.toDeviceX(c.getX()), vp.toDeviceY(c.getY()));
        g.rotate(item.rotationDeg());
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

    private void drawRubberBand(GraphicsContext g) {
        if (mode != Mode.RUBBER_BAND) {
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
