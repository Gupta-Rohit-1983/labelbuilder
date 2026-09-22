# LabelBuilder — Progress Tracker

Companion to [roadmap.md](roadmap.md) (which owns numbering and scope — do not restate scope
here). **Update this file at the end of every sub-phase**, before stopping work.

**Rules recap:** a sub-phase is done only with a green `.\mvnw verify`; commits happen only
when a **full phase** completes (one commit per phase).

**Legend:** ✅ done · 🔄 in progress · ⬜ pending

## Status

| Phase | Sub | Status | Completed | Notes |
|---|---|---|---|---|
| 1 — Architecture & SRS | 1a–1d | ✅ | 2026-07-21 | docs/ committed (`736451f`); roadmap.md re-authored 2026-07-23 after session loss |
| 2 — Project setup | 2a–2c | ✅ | 2026-07-21 | committed `765592c` |
| 3 — Application shell | 3a | ✅ | 2026-07-22 | committed `fb2bdd8` (predates one-commit-per-phase rule) |
| | 3b | ✅ | 2026-07-23 | shell hardening: app dirs, logging, single-instance, exception handler, window state, menus |
| | 3c | ✅ | 2026-07-23 | splash preloader, docking-area placeholder, File-menu accelerators, onHiding shutdown ordering — **Phase 3 complete** |
| 4 — Ribbon | 4a | ✅ | 2026-07-23 | ActionRegistry + AppAction + StatusBus; menus/toolbar registry-generated, FXML layout-only; uncommitted until Phase 4 completes |
| | 4b | ✅ | 2026-07-23 | ribbon control: RibbonSpec (data) + RibbonBuilder (TabPane render); Home + View tabs; replaces 4a toolbar |
| | 4c | ✅ | 2026-07-23 | sealed ItemSpec: split buttons (Save▾Save As live), gallery mechanism, contextual tabs via RibbonContexts |
| | 4d | ✅ | 2026-07-23 | quick-access toolbar (New/Open/Save/Print beside menu bar) + selected-ribbon-tab persistence — **Phase 4 complete** |
| 5 — Docking | 5a | ✅ | 2026-07-23 | DockLayout tree (Center/Group/Split) + transforms, DockPanelRegistry, DockStationBuilder; center-only in shell |
| | 5b | ✅ | 2026-07-23 | DockStation: tab drag sources, 4-side drop-zone overlay, group drop targets; DockMoves pure gestures |
| | 5c | ✅ | 2026-07-23 | DockState (float/auto-hide/dock-back), floating utility windows, edge bars + pin drawer |
| | 5d | ✅ | 2026-07-23 | DockStateCodec + DockStatePreferences (defensive restore); StandardPanels (Toolbox/Objects/Properties/Layers) + default workspace — **Phase 5 complete** |
| 6 — Canvas & rendering | 6a | ✅ | 2026-08-07 | CanvasViewport (mm↔px, zoom/pan/fit), LabelSurface, DesignCanvas (wheel-zoom/middle-pan), CanvasCommands wires View→Zoom; status bar shows mm + zoom% |
| | 6b | ✅ | 2026-08-07 | GridSettings + SnapEngine (pure), Guide; DesignCanvas grid/guides render + drag/delete; Ruler (mm ticks, pointer marker, click-to-add guide); CanvasView frames it |
| | 6c | ✅ | 2026-08-07 | BoundsMm + SelectionModel + ResizeHandle (pure); CanvasItem placeholder; DesignCanvas select/multi-select/move(snap)/resize/rotate/rubber-band |
| | 6d | ✅ | 2026-08-07 | lb-render: RenderScene model (RenderColor/Primitive/Scene), Java2DRenderer reference, ImageComparator + committed PNG baseline regression; FX↔Java2D pixel parity deferred to Phase 19 (TestFX/Monocle) |
| | 6e | ✅ | 2026-08-07 | viewport culling (visibleModelBounds + rotatedAabb), grid clipping to visible∩surface, repaint coalescing — **Phase 6 complete** |
| 7 — Object model & commands | 7a | ✅ | 2026-08-31 | lb-model: pure immutable model — Bounds/RgbaColor + style value types; LabelElement sealed base (composition via ElementProperties) + Text/Rectangle/Ellipse/Line/Image/Barcode-placeholder/Group; document root (LabelDocument/Stock/Layer/GridSpec/Guides). Framework-free (Jackson/FX deferred to 7d); ~40 tests |
| | 7b | ✅ | 2026-08-31 | lb-model.meta: reflection-free property metadata — PropertyDescriptor (typed getter+immutable wither, kind/range/enum-constants) + ElementSchema + ElementSchemas registry; every sealed element type has a schema (test-guarded); common (name/geometry) + per-type props, stroke/fill decomposed to inspector scalars. Drives Phase 9 inspector |
| | 7c | ✅ | 2026-08-31 | lb-core.command: Command (pure forward transform + mergeKey) + CommandStack (snapshot history over immutable LabelDocument: undo/redo, capacity cap, change listeners, mergeable steps); CompositeCommand = transaction (atomic); Add/Remove/Replace/Move/SetProperty commands (SetProperty routes through 7b schema). Undo/Redo actions stay disabled in shell until editor is document-backed (Phase 8) |
| | 7d | ✅ | 2026-08-31 | lb-core.persistence: `.lbl` ZIP format per lbl-format.md — DocumentJson (hand-mapped model↔Jackson tree, byte-stable, forward-tolerant), LabelFiles (manifest+document+assets+thumbnail, schema-version gate), LabelPackage, LblValidator (layer/asset/credential invariants §8). Round-trip byte-identical + deep-equals verified — **Phase 7 complete** |
| 8 — Editing tools | 8a | ✅ | 2026-09-04 | Canvas is now document-backed: DocumentSession (document + CommandStack + id-based selection) replaces placeholder CanvasItem (deleted). SceneMapper (lb-render) flattens LabelDocument→RenderScene; ScenePainter paints it — FX canvas and Java2D renderer now share one description (R-03). ElementKind/ElementFactory + SetBoundsCommand (lb-core); drag-to-create for text/rect/ellipse/line/image/barcode via ribbon Insert group; transforms commit one command per gesture. **Undo/Redo now live**, enablement bound to the stack |
| | 8b | ✅ | 2026-09-04 | Arrange tools: ArrangeOps (pure align/distribute/nudge/duplicate incl. group re-id) + Align/Distribute enums + EditCommands (lb-core); EditActions + observable selectionCount (lb-desktop); ribbon Arrange group + Edit▸Duplicate (Ctrl+D), arrow-key nudge (Shift = ×10). Enablement bound to selection size (align ≥2, distribute ≥3); no-ops never reach the undo stack |
| | 8c | ✅ | 2026-09-22 | Structure & clipboard: Group/Ungroup/Reorder(ZOrder)/AddLayer/UpdateLayer/MoveToLayer commands + delete/group/ungroup/reorder/paste in EditCommands (lb-core); ElementClipboard (in-app) + EditActions cut/copy/paste/delete/group/ungroup/reorder (lb-desktop); Edit menu + ribbon Arrange entries; canvas-scoped Delete and Ctrl+X/C/V (not global, so text fields keep their own clipboard). Objects/Layers panels moved to 8d |
| | 8d | ✅ | 2026-09-22 | Panels & menus: ActionRegistry.createContextMenu + canvas right-click menu (selects under pointer first); ToolboxPanel (buttons generated from the same Insert actions as the ribbon), ObjectsPanel (front-to-back list, two-way selection sync with re-entrancy guard), LayersPanel (visibility/lock toggles, add layer, move selection to layer — all undoable). StandardPanels now serves real views — **Phase 8 complete** |
| 9 — Property inspector | 9a–9d | ⬜ | | |
| 10 — Barcode & QR | 10a–10d | ⬜ | | |
| 11 — Data layer | 11a–11e | ⬜ | | |
| 12 — Variables & expressions | 12a–12c | ⬜ | | |
| 13 — Print engine | 13a–13g | ⬜ | | 🏁 v0.1 ships at 13c |
| 14 — Template & file mgmt | 14a–14d | ⬜ | | |
| 15 — Plugins | 15a–15c | ⬜ | | |
| 16 — Client store & settings infra | 16a–16b | ⬜ | | |
| 17 — Settings & themes | 17a–17c | ⬜ | | |
| 18 — Performance | 18a–18c | ⬜ | | |
| 19 — Testing & QA | 19a–19d | ⬜ | | |
| 20 — Packaging & v1.0 | 20a–20d | ⬜ | | 🏁 v1.0 desktop |
| 21 — Server & multi-user | 21a–21d | ⬜ | | |
| 22 — Print history | 22a–22b | ⬜ | | |
| 23 — Integrations | 23a–23c | ⬜ | | |
| 24 — Licensing & cloud sync | 24a–24c | ⬜ | | 🏁 v2.0 enterprise |

## Environment notes

- Build requires the **JDK 25 toolchain** (upgraded from 21 on 2026-09-04): Temurin 25.0.4.1 at
  `~\.jdks\jdk-25.0.4.1+1`, registered in `~/.m2/toolchains.xml`. Temurin 21.0.11 is kept
  registered alongside it. Verified green on JDK 25 across the full reactor, including the Spring
  context and JavaFX tests — so Spring Boot 3.5 + JavaFX 21 run on Java 25 despite SRS risk R-07.
- On 2026-07-23 the JDK folder was found gutted (likely antivirus/disk cleanup) — if the build
  fails with "No toolchain found", check `bin\java.exe` exists there before anything else.
