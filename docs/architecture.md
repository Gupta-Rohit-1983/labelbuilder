# LabelBuilder — Architecture

**Version:** 0.1 (Phase 1 deliverable)
**Last updated:** 2026-07-21

---

## 1. The governing principle: one model, many renderers

The label is a **pure data structure in millimetres**, with no knowledge of pixels, printers or DPI.
Every output path is a separate visitor over that model.

```
                        LabelDocument (mm, DPI-independent)
                                     │
        ┌──────────────┬─────────────┼──────────────┬──────────────────┐
        ▼              ▼             ▼              ▼                  ▼
   FxRenderer   Java2DRenderer   PdfRenderer   ZplRenderer   EplRenderer / TsplRenderer / DplRenderer
   (designer)   (REFERENCE:      (vector PDF)  (^XA…^XZ)     (native command streams)
                 preview, Win
                 print, PNG)
```

Two consequences that must never be violated:

1. **`Java2DRenderer` is the reference renderer.** What it produces at a given DPI *is* the correct
   output. The FX canvas must match it; PNG-diff regression tests lock this in from Phase 6d. If
   screen and print ever disagree, the screen is wrong.

2. **Command renderers translate semantics, not pixels.** A ZPL printer rasterises nothing — it
   receives `^BCN,100,Y,N,N` and generates the barcode in firmware. So `ZplRenderer` maps a
   `BarcodeElement` to a `^BC` command, *not* to an image. Only elements the language genuinely
   cannot express (free-angle rich text, gradients, unsupported symbologies) fall back to a 1-bit
   raster from `Java2DRenderer` emitted as `^GFA`. Every such fallback is reported to the UI so the
   designer knows an element degraded.

Why this matters: firmware-generated barcodes are sharper, print faster, and scan more reliably than
rasterised ones. A design that rasterises everything would "work" and be quietly bad.

---

## 2. Module structure

Maven multi-module. Arrows point in the direction of dependency.

```
                            ┌───────────┐
                            │ lb-model  │   pure POJOs, zero dependencies beyond Jackson annotations
                            └─────┬─────┘
                                  │
        ┌──────────┬──────────┬───┴────┬──────────┬─────────────┐
        ▼          ▼          ▼        ▼          ▼             ▼
  ┌──────────┐ ┌────────┐ ┌────────┐ ┌──────┐ ┌──────────┐ ┌──────────────┐
  │ lb-core  │ │lb-bar  │ │lb-data │ │      │ │          │ │lb-plugin-api │
  │ commands │ │ code   │ │sources │ │      │ │          │ └──────┬───────┘
  │ expr     │ └───┬────┘ └────┬───┘ │      │ │          │        │
  │ .lbl io  │     │           │     │      │ │          │        │
  └────┬─────┘     │           │     │      │ │          │        │
       │           ▼           │     │      │ │          │        │
       │      ┌─────────┐      │     │      │ │          │        │
       └─────▶│lb-render│◀─────┘     │      │ │          │        │
              └────┬────┘            │      │ │          │        │
                   │                 │      │ │          │        │
                   ▼                 │      │ │          │        │
              ┌─────────┐            │      │ │          │        │
              │lb-print │◀───────────┘      │ │          │        │
              └────┬────┘                   │ │          │        │
                   │                        │ │          │        │
        ┌──────────┴───────────┬────────────┘ │          │        │
        ▼                      ▼              │          │        │
  ┌────────────┐        ┌───────────┐         │          │        │
  │ lb-desktop │        │ lb-server │◀────────┴──────────┴────────┘
  │ (JavaFX)   │───────▶│ (REST)    │
  └────────────┘  HTTP  └─────┬─────┘
        │                     │
        │               ┌─────▼──────────┐
        │               │ lb-automation  │
        │               └────────────────┘
        ▼
  ┌────────────┐
  │  lb-dist   │  jpackage / WiX assembly
  └────────────┘
```

| Module | Contains | Must NOT contain |
|---|---|---|
| `lb-model` | `LabelDocument`, `LabelElement` hierarchy, geometry, units, client↔server DTOs | Any UI, I/O, or framework code |
| `lb-core` | Command stack (undo), expression engine, `.lbl` read/write, document services | JavaFX, Swing, AWT |
| `lb-barcode` | Barcode4J (1D) + ZXing (2D) façade, symbology metadata & validation | Rendering decisions |
| `lb-render` | `Java2DRenderer`, `PdfRenderer`, raster utils, 1-bit dithering | JavaFX |
| `lb-print` | `PrinterDriver` SPI, ZPL/EPL/TSPL/DPL/Windows drivers, RFID, transports | JavaFX |
| `lb-data` | `DataSource` SPI, CSV/Excel/JDBC/REST/prompt implementations | JavaFX |
| `lb-plugin-api` | Stable SPIs third parties implement | Implementation code |
| `lb-desktop` | JavaFX app: shell, ribbon, docking, canvas, inspector, dialogs | Business logic that belongs in core |
| `lb-server` | Spring Boot REST, auth, RBAC, template repo, print history, JPA/Flyway | JavaFX |
| `lb-automation` | Trigger/action engine | JavaFX |
| `lb-dist` | Packaging only | Any source |

**Hard rules:**
- Nothing depends on `lb-desktop`. Ever. If server-side code needs something from the desktop, it
  belongs in a lower module.
- `lb-server` depends on `lb-render` and `lb-print` because it must render and print **headlessly**
  for the REST API and integration engine (FR-A-01). This is why rendering lives in `lb-render`
  rather than in the desktop module — it is the single most important reason for the split.
- `lb-model` has no dependencies. It is shared verbatim between client and server.

---

## 3. Package naming

Base package `com.rohit.labelbuilder`, one segment per module:

```
com.rohit.labelbuilder.model      .core      .barcode   .render
                      .print      .data      .plugin    .desktop
                      .server     .automation
```

Within `lb-desktop`: `.desktop.shell`, `.desktop.ribbon`, `.desktop.dock`, `.desktop.canvas`,
`.desktop.inspector`, `.desktop.dialog`, `.desktop.action`.

---

## 4. Threading model

JavaFX has one UI thread. Violating this is the most common way to produce intermittent, hard-to-
reproduce failures, so the rules are absolute.

| Work | Thread | Mechanism |
|---|---|---|
| All scene-graph and model mutation | FX Application Thread | Direct |
| Database queries, REST calls, file I/O | Spring `TaskExecutor` pool | `CompletableFuture` |
| Print job merge + transmission | Dedicated single-thread print executor per printer | Serialised — a printer accepts one job at a time |
| Rendering to PNG/PDF for export | `TaskExecutor` pool | `Java2DRenderer` is stateless & thread-safe |
| Publishing results back to UI | FX Application Thread | `Platform.runLater` |

- **The FX thread must never block.** No JDBC, no HTTP, no file reads on it.
- The **model is FX-thread-confined**. Background work operates on immutable snapshots or DTOs, not
  on the live document.
- `Java2DRenderer` and all command renderers are **stateless** — they take `(document, record, dpi)`
  and return output. This is what lets the server render concurrently for many users.

---

## 5. Key contracts

These interfaces are the load-bearing seams. They are defined early (Phases 7, 11a, 13d) because
everything else plugs into them.

### 5.1 `LabelElement` (lb-model, Phase 7a)
Base of the element hierarchy. Position and size in **mm**, origin top-left of the label.
Carries id, name, bounds, rotation, z-order, layer, locked, visible. Subtypes add their own typed
properties, declared through the property-metadata system (Phase 7b) so the Property Inspector and
expression engine are generated rather than hand-written per type.

### 5.2 `Command` / `CommandStack` (lb-core, Phase 7c)
```
interface Command { void execute(); void undo(); String description(); }
```
**Every** mutation is a command. The stack supports transactions (compound operations undo as one)
and coalescing (a 200-event drag becomes one undo step). Enforced by convention plus a Checkstyle/
ArchUnit rule that model setters are not called outside `lb-core`.

### 5.3 `DataSource` / `RecordSet` / `Record` (lb-data, Phase 11a)
```
interface RecordSet extends Iterable<Record>, AutoCloseable { int size(); Record get(int i); }
```
**Cursor-based and streaming.** Never `List<Record>` an entire table — NFR-05 requires a 10 000-record
batch at 1 GB heap. Implementations: `manual`, `csv`, `excel`, `jdbc`, `rest`.

### 5.4 `PrinterDriver` + `PrinterCapabilities` (lb-print, Phase 13d)
```
interface PrinterDriver {
    PrinterCapabilities capabilities();
    PrintOutput generate(LabelDocument doc, Record record, PrintOptions opts);
}
```
`PrinterCapabilities` declares DPI, max print width, resident fonts, supported symbologies, and
cutter/peeler/**RFID** support. The UI reads capabilities to disable what a printer cannot do
(FR-R-05) and to warn about unreproducible X-dimensions (FR-B-07).

### 5.5 `Transport` (lb-print, Phase 13d)
Sends bytes to a device: `RawTcpTransport` (:9100), `WindowsRawSpoolTransport`, `SerialTransport`,
`FileTransport` (for testing/golden files).

---

## 6. `.lbl` file format

A ZIP archive. Full schema in [`lbl-format.md`](lbl-format.md).

```
mylabel.lbl
├── manifest.json      schema version, app version, created/modified, thumbnail ref
├── document.json      the LabelDocument, Jackson polymorphic on element type
├── thumbnail.png      library preview
└── assets/
    ├── logo.png
    └── barcode-font.ttf
```

- `manifest.json` carries `schemaVersion`; a migration chain upgrades older documents on open
  (FR-M-05). Migrations are one class per version step, chained — never a giant switch.
- **Credentials are never written here** (FR-DS-09). A document references a data-source *config id*;
  the connection details and secrets live in the client H2 store or on the server.

---

## 7. Client/server split

| Lives on the server | Lives on the client |
|---|---|
| Users, roles, permissions | Window layout, theme, recent files |
| Shared template library + version history | Offline template cache |
| Serial-number counters (must be globally unique — FR-V-05) | Local scratch documents |
| Printer profiles (shared) | Locally-attached printer overrides |
| Data-source configs + encrypted credentials | Cached credentials for offline grace |
| Print history & audit trail | Local print log (synced when online) |
| Integration definitions + the running engine | Integration Builder UI |

**Permissions are enforced server-side (FR-S-05).** The client hides disallowed actions for UX, but
a tampered client must still be refused by the API. Any design that only checks permissions in the
JavaFX layer is wrong.

**Offline behaviour (FR-S-04, NFR-09):** the client caches its JWT, its permission set, and recently
used templates. During the grace period it can open cached templates and print, queueing history
records for upload. Serial-number allocation is the exception — the client requests a *block* of
serials while online and consumes from it offline, so numbers stay unique.

---

## 8. Server data model (outline, Phase 21a)

```
users ──< user_roles >── roles ──< role_permissions >── permissions
templates ──< template_versions
printer_profiles
data_source_configs   (secrets encrypted at rest)
serial_counters       (template_id, field_name, current_value)  — row-locked on allocation
print_jobs ──< print_job_records   (merged data snapshot, RFID EPC, per-record result)
audit_events
integrations ──< integration_runs
```

SQL Server via Spring Data JPA, schema managed by **Flyway** — never `ddl-auto`. `serial_counters`
allocation uses a `SELECT … FOR UPDATE`-equivalent row lock so concurrent clients cannot collide.

---

## 9. JavaFX ↔ Spring integration

`LabelBuilderApp.main()` starts the Spring context, then launches the JavaFX `Application`:

```java
fxmlLoader.setControllerFactory(springContext::getBean);
```

Controllers become Spring beans and can inject services. The Spring context outlives the JavaFX
stage; shutdown closes both cleanly (flush autosave, close H2, cancel print jobs).

The desktop app uses `spring-boot-starter` only — **not** `spring-boot-starter-web`. It is not a
server; adding the web starter would boot Tomcat inside the desktop app.

---

## 10. Known build-from-scratch components

JavaFX lacks maintained libraries for two things this app needs. Both are built in-house, kept
deliberately minimal:

- **Ribbon (Phase 4)** — `TabPane`-based, with groups, large/small buttons, split buttons, galleries,
  a quick-access toolbar and contextual tabs. Driven entirely by a central `ActionRegistry` so a
  command is defined once and reused by ribbon, menus, context menus and accelerators (FR-X-02).
- **Docking (Phase 5)** — nested `SplitPane`/`TabPane` with drag-to-dock, drop-zone overlay,
  float/pin/auto-hide, and persisted layouts. DockFX and AnchorFX were evaluated and rejected as
  unmaintained.

---

## 11. Technology decisions and rationale

| Decision | Rationale | Rejected alternative |
|---|---|---|
| Barcode4J for 1D | True X-dimension and quiet-zone control; ZXing's 1D API doesn't expose them properly | ZXing for everything |
| ZXing for 2D | Best-maintained QR/DataMatrix/PDF417/Aztec encoder in Java | Barcode4J (no 2D) |
| PDFBox for PDF | Vector output with embedded fonts, permissive licence | iText (AGPL/commercial) |
| Whitelisted expression evaluator | Raw SpEL is remote code execution in a multi-user product (R-04) | Raw SpEL, Groovy, JS engine |
| Flyway migrations | Deterministic, reviewable schema evolution | Hibernate `ddl-auto` |
| H2 file mode on the client | Zero-install local store for settings/cache/profiles | Flat files, Windows registry |
| JDK 21 LTS, enforced by Maven toolchain | Everything we depend on — Spring Boot 3.x, JavaFX 21, Barcode4J, PDFBox — is tested against it; JDK 26 outruns Spring Boot 3.5's supported range | Building on JDK 26 with `--release 21` |
| Model in millimetres | Physical accuracy is the product's core promise (NFR-07); pixels are a rendering concern | Model in pixels/points |

---

## 12. Build & run

```powershell
.\mvnw clean verify                 # full build + tests
.\mvnw -pl lb-desktop javafx:run     # run the desktop app
.\mvnw -pl lb-server spring-boot:run # run the server
.\mvnw -pl lb-dist package -Pdist    # build the MSI
```

Maven 3.9.9 is already cached in `~/.m2/wrapper/dists`, so the wrapper works without network access.
