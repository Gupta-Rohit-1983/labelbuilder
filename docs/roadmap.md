# LabelBuilder — Project Roadmap

**Version:** 1.0 — authored 2026-07-23. **This file is the single source of truth for phase
numbering and scope.** Progress (what is done / in flight) is tracked separately in
[progress.md](progress.md); this file describes the plan, not the status.

> **Provenance.** The original 20-phase plan was refined during Phase 1 into the numbering used
> throughout the committed docs (SRS, architecture, api-draft, rbac-matrix, lbl-format). That
> refined plan was lost with a working session; this document reconstructs it. Every phase
> reference that appears in the committed docs (4, 5, 6d, 7a–7d, 11a, 13c–13g, 17, 18, 19,
> 21a–21c, 22, 23) is preserved exactly as the docs use it. Phases with no surviving doc
> reference (3c, 8, 9, 10, 12, 14, 15, 16, 20, 24) were re-authored on 2026-07-23.

## Working agreement

- Every phase is divided into **sub-phases (a, b, c…)**. A sub-phase is a safe stopping point:
  it ends with a green `.\mvnw verify` and an updated [progress.md](progress.md).
- **Commits happen only when a full phase completes** — never per sub-phase. (Exception already
  in history: `fb2bdd8` committed 3a alone, before this rule existed.)
- Build runs on the **JDK 21 toolchain** (SRS §5, risk R-07); JDK 26 alone will not build.

## Milestones

| Milestone | At | Meaning |
|---|---|---|
| **v0.1** | end of 13c | Design a static label and print it — first shippable value (risk R-05) |
| **v1.0 desktop** | end of Phase 20 | Feature-complete standalone designer, installed via MSI |
| **v2.0 enterprise** | end of Phase 24 | Server, multi-user, RBAC, automation, licensing, cloud sync |

## Requirements → phase map

| Requirement | Phase(s) |
|---|---|
| Label designer with drag-and-drop | 5–9 |
| Barcode generation | 10 (1D) |
| QR code generation | 10 (2D) |
| Database connections (SQL Server, MySQL, Excel, CSV, …) | 11 |
| Variable fields | 12 |
| Serial number generation | 12 |
| Print preview | 13b |
| Direct printer support (Zebra, TSC, …) | 13d–13e |
| RFID support | 13f |
| Batch printing | 13g |
| Template saving/loading | 7d, 14 |
| Multi-user support | 21 |
| Role-based permissions | 21c |
| Print history/logging | 22 |
| Automation/API | 21a (API), 23 (automation) |
| Integration Builder equivalent | 23 |
| Licensing system | 24 |
| Cloud synchronization | 24 |

---

## Phase 1 — Software Architecture & SRS *(1–2 weeks)*

No coding; the blueprint. Delivered as `docs/`: SRS (FRs, NFRs, use cases), architecture
(modules, seams, tech decisions), API draft, RBAC matrix, `.lbl` format spec, this roadmap.

- **1a** — SRS: functional + non-functional requirements, use cases, user stories
- **1b** — Architecture: module breakdown, load-bearing interfaces, tech decisions
- **1c** — Server API draft, RBAC matrix, `.lbl` file-format spec
- **1d** — Roadmap, coding standards, git strategy

## Phase 2 — Multi-Module Project Setup *(2–4 days)*

- **2a** — Maven parent + all module POMs (`lb-model`, `lb-core`, `lb-render`, `lb-barcode`,
  `lb-data`, `lb-print`, `lb-plugin-api`, `lb-desktop`, `lb-server`, `lb-automation`,
  `lb-dist`, `lb-arch`), JDK 21 toolchain enforcement
- **2b** — Quality gates: Spotless, enforcer, ArchUnit skeleton (`lb-arch`)
- **2c** — CI: GitHub Actions build with Temurin 21 + toolchain registration

## Phase 3 — JavaFX Application Shell *(1 week)*

- **3a** — Spring–JavaFX bootstrap: `FxApplication` starts the context, `StageReadyEvent`,
  FXML loading with Spring controller factory, context smoke test
- **3b** — Shell hardening: app-data directory tree (`%APPDATA%\LabelBuilder`), Logback file
  logging, single-instance guard, global exception handler + error dialog, window-state
  persistence, menu bar, toolbar, sectioned status bar, shell CSS
- **3c** — Startup polish: splash/startup screen, docking-area placeholder region, base menu
  accelerators (Ctrl+N/O/S…), graceful-shutdown ordering (flush state → close context)

## Phase 4 — Ribbon Framework *(1 week; build-from-scratch, risk R-01)*

Driven entirely by a central `ActionRegistry`: a command is defined once and reused by ribbon,
menus, context menus and accelerators (FR-X-02, architecture §10).

- **4a** — `ActionRegistry`: command definitions, enablement, accelerator binding; rewire the
  Phase 3 menus/toolbar onto it
- **4b** — Ribbon control: `TabPane`-based tabs, groups, large/small buttons
- **4c** — Split buttons, galleries, contextual tabs
- **4d** — Quick-access toolbar + ribbon state persistence

## Phase 5 — Docking Framework *(1 week; build-from-scratch, risk R-01)*

DockFX/AnchorFX rejected as unmaintained (architecture §10). Kept minimal and API-small.

- **5a** — Docking core: nested `SplitPane`/`TabPane` model, programmatic layout
- **5b** — Drag-to-dock with drop-zone overlay
- **5c** — Float / pin / auto-hide
- **5d** — Layout persistence + empty standard panels registered (Property Inspector, Object
  Explorer, Layers, Toolbox)

## Phase 6 — Canvas & Rendering Engine *(3–4 weeks; the heart of the software)*

`Java2DRenderer` is the single visual reference; the FX canvas must match it (risk R-03).
Model coordinates are millimetres (NFR-07).

- **6a** — Canvas viewport: label surface, mm coordinate system, zoom, pan
- **6b** — Grid, snap, rulers, guides
- **6c** — Selection, multi-selection, move/resize/rotate handles, mouse interaction
- **6d** — `Java2DRenderer` reference renderer + **PNG-diff regression suite** locking FX ↔
  Java2D parity (referenced from architecture §2)
- **6e** — Rendering optimization first pass (dirty regions, culling)

## Phase 7 — Label Object Model, Commands & Persistence *(2 weeks)*

The load-bearing seams, defined early (architecture §5).

- **7a** — `LabelElement` base + concrete elements: text, rectangle, circle, line, polygon,
  image, barcode placeholder, group, layer, lock, visibility (lb-model)
- **7b** — Property-metadata system so the Property Inspector and serializer discover
  properties without reflection hacks
- **7c** — `Command` / `CommandStack`: undo, redo, history, transactions for multi-step
  operations (lb-core)
- **7d** — `.lbl` file format: save/load per the committed spec (docs/lbl-format.md)

## Phase 8 — Editing Tools *(2 weeks)*

- **8a** — Creation tools: text, shape, image, barcode placement on canvas
- **8b** — Alignment, distribution, duplicate, nudge
- **8c** — Clipboard (cut/copy/paste), delete, grouping, layer operations
- **8d** — Context menus + toolbox panel wired to tools

## Phase 9 — Property Inspector *(2 weeks)*

- **9a** — Inspector framework driven by the 7b property metadata
- **9b** — Core editors: font, size, color, position, rotation, opacity
- **9c** — Per-type property pages (text, shapes, image: scale/crop; barcode: symbology,
  height, check digit, quiet zone)
- **9d** — Multi-select editing + undo integration

## Phase 10 — Barcode & QR Engine *(3 weeks)*

Barcode4J for 1D (true X-dimension and quiet-zone control), ZXing for 2D (architecture §11).

- **10a** — 1D: Code128, Code39, EAN13, UPC, GS1, ITF, Codabar
- **10b** — 2D: QR, PDF417, DataMatrix, Aztec (incl. error correction)
- **10c** — Validation, auto-sizing, check digits, quiet zones
- **10d** — Canvas/renderer parity for barcode elements + live preview in inspector

## Phase 11 — Data Layer *(3 weeks)*

- **11a** — `DataSource` / `RecordSet` / `Record` interfaces — `RecordSet` is **cursor-based,
  never `List<Record>`** (risk R-06)
- **11b** — File sources: CSV, Excel, JSON, XML
- **11c** — JDBC sources: SQL Server, MySQL, PostgreSQL, Oracle, SQLite + Connection Manager
  (credentials never in `.lbl` — risk R-08)
- **11d** — Query builder, parameterized queries, live preview
- **11e** — Data binding: bind element properties to record fields

## Phase 12 — Variable & Expression Engine *(2 weeks)*

- **12a** — Variable framework: date, time, counter, **serial numbers**, random, user input,
  database fields
- **12b** — Expression evaluator — **whitelisted function registry, never raw SpEL**
  (risk R-04): IF, CASE, CONCAT, math, string, regex
- **12c** — Prompt-at-print user input + persistent serial/counter state

## Phase 13 — Print Engine *(3 weeks)*

- **13a** — Print pipeline via `Java2DRenderer`, printer discovery
- **13b** — Print preview + print dialog: copies, DPI, rotation, scaling
- **13c** — End-to-end: design a static label and print it — **🏁 ship v0.1** (risk R-05)
- **13d** — `PrinterDriver` + `PrinterCapabilities` + `Transport` SPI (architecture §5)
- **13e** — Thermal drivers: Zebra **ZPL** + TSC **TSPL**, validated against Labelary from day
  one and against real hardware (SRS assumption §6, Q-1)
- **13f** — RFID encode support (hardware-dependent, Q-1)
- **13g** — Printer profiles + batch printing
- Transports: TCP:9100, Windows RAW queue (SRS §6)

## Phase 14 — Template & File Management *(2 weeks)*

- **14a** — New / Open / Save / Save As / Recent files (real handlers replacing 3b stubs)
- **14b** — Autosave + crash recovery (the `recovery\` app-data directory)
- **14c** — Template versioning
- **14d** — Export: PDF (PDFBox — architecture §11) and PNG

## Phase 15 — Plugin Architecture *(2 weeks)*

- **15a** — Plugin API (`lb-plugin-api`) + loader with dynamic loading
- **15b** — Plugin manager UI
- **15c** — Example plugins: custom barcode type, custom data source, custom printer driver

## Phase 16 — Client Data Store & Settings Infrastructure *(1 week)*

- **16a** — H2 file-mode local store (architecture §11): settings, printer profiles, offline
  cache; Flyway migrations
- **16b** — Preferences service consolidating scattered state (window state, recents, ribbon
  and docking layouts) onto one store

## Phase 17 — Settings & Themes *(1 week)*

- **17a** — Settings dialog: general, autosave, performance, fonts
- **17b** — Light + dark themes (replaces the Phase 3 neutral shell CSS)
- **17c** — Localisation scaffolding — English only for v1, bundles in place (Q-4)

## Phase 18 — Performance Optimization *(2 weeks)*

Goal: 60 FPS with thousands of objects; sizing per Q-3 (default 25 concurrent users,
50 k labels/day for server phases).

- **18a** — Canvas/rendering: profiling, dirty-region and culling improvements over 6e
- **18b** — Memory: image cache, lazy loading, leak hunting
- **18c** — Data/print throughput: streaming record sets under load, thread pool tuning

## Phase 19 — Testing & Quality Assurance *(3 weeks)*

The test suite **indexes its tests by FR ID** (SRS §9 traceability).

- **19a** — Unit coverage push (JUnit + Mockito) across modules, FR-indexed
- **19b** — UI tests (TestFX) for designer workflows
- **19c** — Integration + regression: data sources, print pipeline, PNG-diff suite expansion
- **19d** — Performance + memory-leak test harness

## Phase 20 — Packaging, Documentation & v1.0 Release *(2 weeks)*

- **20a** — `jpackage` + WiX → MSI (`lb-dist`), desktop shortcut, portable version
- **20b** — Auto-update mechanism + (optional) digital signature
- **20c** — Docs: user manual, installation guide, developer/architecture guide, API docs
- **20d** — Release notes — **🏁 v1.0 desktop**

## Phase 21 — Server Core & Multi-User *(3 weeks)*

`lb-server`: Spring Boot web/data/security; SQL Server primary, PostgreSQL supported (SRS §5).

- **21a** — Server data model + Flyway schema + REST API skeleton per docs/api-draft.md;
  OpenAPI spec published from here
- **21b** — Authentication: app-managed accounts (AD/LDAP later per Q-2), token issuance
- **21c** — RBAC per docs/rbac-matrix.md; server-side enforcement (risk R-08)
- **21d** — Desktop client ↔ server: login, template store browse/open/save

## Phase 22 — Print History & Logging *(1 week)*

- **22a** — Server-side print job recording + retention policy (Q-6 default: 7 years)
- **22b** — History browsing UI + reprint from history

## Phase 23 — Integrations / Automation *(2–3 weeks)*

The Integration Builder equivalent (`lb-automation`), per docs/api-draft.md §10: triggers
(watch folder, HTTP), transforms (CSV field mapping), actions (print, move file,
email-on-error), dry-run testing, execution logs.

- **23a** — Integration engine: trigger/transform/action pipeline + persistence
- **23b** — Watch-folder + HTTP triggers, print action, run logging
- **23c** — Integration Builder UI + dry-run with sample data (FR-A-06)

## Phase 24 — Licensing & Cloud Sync *(2 weeks)*

- **24a** — Licensing system: license issuance/validation, feature gating
- **24b** — Cloud synchronization: template/settings sync through the server
- **24c** — Hardening pass + release — **🏁 v2.0 enterprise**
