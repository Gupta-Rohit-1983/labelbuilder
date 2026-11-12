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

### 5.3 `DataSource` / `RecordSet` / `DataRecord` (lb-data, Phase 11a — implemented)
```
interface RecordSet extends Iterable<DataRecord>, AutoCloseable {
    List<FieldDefinition> fields();
    boolean next();           // advance; false when exhausted
    DataRecord current();     // the row the cursor is on
    OptionalLong size();      // empty when the source cannot know
    void close();
}
```
**Cursor-based and streaming.** Never `List<DataRecord>` an entire table — NFR-05 requires a
10 000-record batch at 1 GB heap. Implementations: `manual`, `csv`, `excel`, `jdbc`, `rest`.

Two deliberate changes from this section's original sketch, made when implementing it:

- **No `Record get(int i)` and no `int size()`.** Random access and a known row count are precisely
  what a forward-only cursor cannot offer; supporting them would force the buffering R-06 exists to
  prevent. `size()` is an `OptionalLong`, and a streaming source answers empty rather than counting
  by fetching everything.
- **`DataRecord`, not `Record`.** A type named `Record` shadows `java.lang.Record` for every class in
  the same package — a trap in the module most likely to declare records of its own.

`AbstractRecordSet` holds the cursor state machine (no current row before the first `next()` or after
exhaustion, exhausted latches, a closed cursor refuses, `close()` is idempotent) so each format only
implements `advance()`. Every source inherits it, which is why the contract is uniform rather than
re-derived four times.

### 5.3.1 File sources (lb-data, Phase 11b — implemented)

`CsvDataSource`, `ExcelDataSource`, `JsonDataSource`, `XmlDataSource`, reached through
`FileDataSources.forFile`. Three rules they share, each chosen for the printed output rather than for
the data model:

- **Values are handed over as the file holds them.** `1.50` stays `"1.50"`, not the double 1.5; a
  price label missing its pence is a defect somebody has to explain to a customer. The inferred
  `FieldType` is metadata for binding, sorting and the query builder — never a conversion
  instruction. Excel is included: its cells are read through their own number format, and formulas
  through their **cached** results, so what prints is what the operator approved on screen.
- **`TypeInference` keeps ambiguous columns textual.** A leading zero (`0012`) or a digit string too
  long for a `long` (an SSCC, an EPC) stays `STRING`, because a consumer that round-trips it through a
  number drops exactly the digits a barcode needs. `1`/`0` are quantities, not booleans, and
  locale-formatted dates stay text rather than being read in the wrong convention.
- **Nothing is dropped silently.** Blank and duplicate header cells are renamed positionally
  (`Column2`, `Description_2`), rows wider than the schema keep their extra cells, and a short row
  leaves the column present but empty. A malformed export should be visible, not quietly trimmed.

XML refuses DTDs and external entities: a feed is often supplied by whoever is sending the order, and
the default StAX configuration resolves `file:///…` entities (XXE).

### 5.3.2 JDBC sources + Connection Manager (lb-data, Phase 11c — implemented)

```
JdbcDataSource(name, ConnectionManager, configId, sql, cachedFields?)
ConnectionManager.connect(configId) → resolves the secret from CredentialStore, then wipes it
```

**Risk R-08 is structural here, not procedural.** `ConnectionConfig` has no password component, and its
constructor rejects a password-shaped property key or a URL carrying `password=`/`pwd=`/`user:secret@`.
A credential is *inexpressible* in the object a template refers to, so the `.lbl` invariant §8.5 and
`LblValidator.assertNoCredentialLeak` are a last line of defence rather than the only one. The secret
is resolved by config id at the moment a connection opens, as a `char[]` the manager wipes afterwards.

`CredentialStore` has two implementations: `InMemoryCredentialStore` (this session only — a
"remember for now" prompt, and the offline grace period of FR-S-04) and `SealedCredentialStore`
(AES-GCM via `SecretCipher`, which is the encryption only — **where the key lives is deliberately not
decided here**, since the client's store arrives in Phase 16a and the server's in Phase 21, and each
will hold it differently). GCM rather than CBC so a tampered store fails to open instead of
decrypting to rubbish that then gets sent to a production database as a password.

Three other decisions worth knowing:

- **`JdbcDialect.streamingFetchSize()` is load-bearing.** On MySQL, Connector/J reads the entire
  result set into the client unless the fetch size is exactly `Integer.MIN_VALUE`. A cursor
  *interface* cannot deliver streaming on its own — the driver has to be told, per vendor — so R-06
  depends on this method being right.
- **The schema is read without running the query.** `PreparedStatement.getMetaData()` describes the
  result shape, so designing against a report that takes a minute costs nothing; a cached schema
  short-circuits even that, which is how a template stays editable with the database out of reach.
- **Parameters are always bound, never substituted.** `NamedQuery` rewrites `:name` to `?` (skipping
  literals, quoted and bracketed identifiers, comments and PostgreSQL `::` casts) — the same stance as
  R-04 takes on expressions: the dangerous construct is made unavailable rather than made safe. A
  parameter with no supplied value is refused, because binding NULL would succeed and return the wrong
  rows.

Connections are not pooled: a merge opens a cursor, streams it and closes it, and a pool would keep
connections — each holding a resolved credential — alive past the job that needed them. `connect` is
the seam if the server ever needs pooling.

### 5.3.3 Query builder & live preview (lb-data, Phase 11d — implemented)

```
QuerySpec  ──SqlRenderer.render(spec, dialect)──▶  RenderedQuery  ──▶  BuiltQuerySource
                                                   (sql + bound literals)
DataPreviews.of(anyDataSource, params, maxRows) ──▶ DataPreview
```

**The query is stored structurally, not as text.** `QuerySpec` keeps the from/columns/joins/filters/
sort/limit, so a query reopens in the builder next month and re-renders for whatever dialect the
connection now points at. A template holding only a SQL string would be editable in the SQL editor
alone, and wrong the moment the database moved from SQL Server to PostgreSQL.

**`SqlRenderer` has no code path that concatenates a value.** A literal the operator typed becomes a
generated `:p1` placeholder whose value goes in `RenderedQuery.literalValues()`; a declared parameter
keeps its own name. The builder therefore cannot emit an injectable statement whatever is typed into
it, and the class deliberately offers no "quote a value" helper to misuse — the same stance R-04 takes
on expressions. Identifiers *are* written into the text, quoted per dialect, because they have to be;
they come from the schema browser rather than from free text. The catalog is left out of a table
reference on purpose: a three-part name ties a query to one database, and a template promoted from
test to production would read from test.

**`ParameterValues` converts, because everything that supplies a parameter supplies text** — a prompt
field, a command line, a watched file's name. Bound as text, `qty >= '7'` compares as a string, so
`"10" < "5"` and the run selects the wrong products with no exception and no empty result. The
declared `FieldType` decides the conversion; a value that cannot be converted is an error while the
operator can still retype it. Blank input counts as *absent* so an optional parameter's default stays
reachable, and every missing required parameter is reported in one message rather than one per attempt.

**`DataPreview` is the one place rows are deliberately materialised.** The exception is bounded: at
most `DEFAULT_MAX_ROWS` (100), one extra read to report `truncated()`, then the cursor is closed —
R-06 forbids holding a table to print it, not holding a hundred rows to show them. The preview never
throws, because a broken query, a missing file and an unreachable server are all things the pane has
to display; and it carries the columns even when nothing matched, so a label can be laid out against a
query that returns no rows today. It takes the `DataSource` interface, so one implementation previews
CSV, Excel, JSON, XML and JDBC alike — the dividend from making `DataSource` a factory for cursors in
11a rather than five reader APIs.

A note for anyone adding ordered output here: `Map.copyOf` does **not** preserve insertion order, and
an immutable map's iteration order is scrambled by a salt randomised once per JVM. `RenderedQuery`
uses an unmodifiable `LinkedHashMap` so the bound values list in the order they appear in the SQL;
using `copyOf` made that order differ between a single-module test run and the full reactor.

**Known limit (FR-DS-02, Phase 18):** POI parses a workbook into memory, because `.xlsx` is a ZIP of
XML with a shared string table rather than a line-oriented stream. The cursor still yields one row at
a time, so the `RecordSet` contract holds, but a very large workbook is bounded by the file rather
than by the cursor. CSV is the answer for large batches until a SAX-streaming reader lands.

### 5.3.4 Data binding & the print form (lb-data, Phase 11e — implemented)

```
DocumentBinder.bind(template, Map<sourceName, DataRecord>) → BindingResult(document, problems)
PromptDataSource(name, promptFields).open(answers)         → one validated record
```

Bindings live **inside property values** as `${Source.Field}`, exactly as lbl-format.md §4.2 and §4.3
store them — there is no separate binding table to keep in step with the elements. `ValueTemplate`
splits a value into literal text and placeholders once; `resolve` then runs per record, which is what
makes a 10 000-label run cheap.

**The binder has no per-element-type code.** Which properties carry printed content is declared by
`PropertyDescriptor.bindable()` in the Phase 7b metadata — `value` on text and barcode,
`hriCustomText`, `assetPath` on an image — so `DocumentBinder` asks the schema and never switches on
an element class. An element's `name` is deliberately not bindable (design metadata that never
prints), and neither is `printCondition`: that is an expression for the Phase 12 engine to
*evaluate*, and substituting a value into it would turn a condition into a constant.

**Only field references resolve here.** Anything else inside `${…}` — a function call, an operator, a
bare `${Serial}` — is kept verbatim and reported as a warning, because evaluating it belongs to the
Phase 12 engine (which R-04 requires to be a whitelisted registry, not something improvised in the
binder). Inventing semantics for those now would have to be unpicked later.

**Problems are reported, not thrown**, the same split as the barcode validator: an unknown source or
field is an `ERROR` and leaves the placeholder *visible* on the label, because a silently blanked
barcode scans as nothing and nobody questions it, whereas `${Product.Nope}` printed on a carton is
noticed before the run leaves the building. The designer shows the problems; the print engine refuses
on any error. The merged document is pure output — the template is never mutated, so a failure on row
4 000 cannot have corrupted it for row 4 001.

`PromptDataSource` (FR-DS-08) is an ordinary `DataSource` yielding one record, so `${Prompt.SKU}`
binds exactly like a database column and the preview, binder and print engine need no special case.
Its answers arrive through `open(parameters)` — what that parameter was always for. Required fields,
the validation regex and the pick list are all checked **before any label prints**, and every failure
is reported together: an operator at a terminal should see everything wrong with the form at once. A
malformed validation pattern is refused when the field is *defined*, since otherwise it would fail
every answer at print time with no way to tell why.

### 5.4 `PrinterDriver` + `PrinterCapabilities` (lb-print, Phase 13d)
```
interface PrinterDriver {
    PrinterCapabilities capabilities();
    PrintOutput generate(LabelDocument resolvedDocument, PrintOptions opts);
}
```
`PrinterCapabilities` declares DPI, max print width, resident fonts, supported symbologies, and
cutter/peeler/**RFID** support. The UI reads capabilities to disable what a printer cannot do
(FR-R-05) and to warn about unreproducible X-dimensions (FR-B-07).

**A driver takes an already-merged document, not a template plus a record.** The sketch here
originally passed a record alongside the template, which §2's module table forbids — `lb-print` may
not depend on `lb-data`, and the ArchUnit layering rule enforces it. Correcting the signature is the
better answer rather than relaxing the rule: a driver that received a record would have to resolve
bindings itself, so every driver would re-implement the merge, and the golden-file tests for ZPL and
EPL output would each need data fixtures. Binding happens once, in `DocumentBinder` (§5.3.4), and the
driver's only job is turning a finished document into bytes. The caller that owns both — the desktop
app, the automation engine or the server — merges and then prints.

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
| JDK 25 LTS, enforced by Maven toolchain | Everything we depend on — Spring Boot 3.x, JavaFX 21, Barcode4J, PDFBox — is tested against it; JDK 26 is reserved for future compatibility work | Building on JDK 26 with `--release 25` |
| Model in millimetres | Physical accuracy is the product's core promise (NFR-07); pixels are a rendering concern | Model in pixels/points |

---

## 12. Build & run

```powershell
.\mvnw clean verify                 # full build + tests
.\mvnw install                       # once after module changes: puts sibling jars in ~/.m2 so -pl runs resolve
.\mvnw -pl lb-desktop javafx:run     # run the desktop app
.\mvnw -pl lb-server spring-boot:run # run the server
.\mvnw -pl lb-dist package -Pdist    # build the MSI
```

`-pl` builds a single module and resolves siblings from the local repository, not the source tree —
hence the `install` prerequisite whenever sibling modules changed since the last install.

Maven 3.9.9 is already cached in `~/.m2/wrapper/dists`, so the wrapper works without network access.
