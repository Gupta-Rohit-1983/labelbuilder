# LabelBuilder — Software Requirements Specification

**Version:** 0.1 (Phase 1 deliverable)
**Status:** Draft
**Last updated:** 2026-07-21

---

## 1. Purpose & Scope

LabelBuilder is a Windows desktop application for designing, managing and printing labels —
functionally equivalent to Seagull Scientific **BarTender**. It targets manufacturing, warehouse,
retail and regulated-industry environments where labels must carry accurate variable data, scannable
barcodes, and (optionally) encoded RFID, with a full audit trail of what was printed and by whom.

**In scope:** WYSIWYG label design, barcode/QR/RFID generation, live data binding from
SQL Server/MySQL/Excel/CSV/REST, print preview, printing to both Windows drivers and thermal
printers via native command languages, batch printing, multi-user access control, automation, and
print history/audit logging.

**Out of scope (v1):** reading proprietary BarTender `.btw` files; standalone RFID reader/writer
hardware SDKs; label *verification* scanners; macOS/Linux desktop builds; mobile clients.

---

## 2. Actors & Roles

| Actor | Description |
|---|---|
| **Administrator** | Manages users, roles, printers, server configuration, retention policy. |
| **Designer** | Creates and edits label templates, configures data sources and integrations. |
| **Operator** | Opens existing templates and prints them. Cannot enter design mode. |
| **Auditor** | Read-only access to print history and audit trail. Cannot print or design. |
| **Integration Service** | Non-human actor. Triggers print jobs via API/watch folder with a service account. |

The full permission matrix is in [`rbac-matrix.md`](rbac-matrix.md).

---

## 3. Functional Requirements

Requirement IDs are stable and referenced from tests and phase deliverables.

### 3.1 Label Designer (FR-D)

| ID | Requirement | Phase |
|---|---|---|
| FR-D-01 | Canvas displays the label at true physical size at 100% zoom, in mm or inches. | 6a |
| FR-D-02 | Zoom 25%–1600% via wheel+Ctrl, fit-to-window, and 100% preset; pan by middle-drag or space-drag. | 6a |
| FR-D-03 | Rulers, configurable grid, and user-draggable guides, all in the active unit. | 6b |
| FR-D-04 | Page shows stock boundary, margins, and printer non-printable area. | 6a |
| FR-D-05 | Elements are added by drag-and-drop from a Toolbox onto the canvas. | 8c |
| FR-D-06 | Element types: Text, Barcode, QR/2D, Image, Line, Rectangle, Ellipse, Table, Group. | 7a |
| FR-D-07 | Selection: click, Shift+click, rubber-band, Ctrl+A, Tab cycling. | 8a |
| FR-D-08 | Transform: 8 resize handles, rotate handle, aspect lock, arrow-key nudge (1 px / Shift 10 px). | 8b |
| FR-D-09 | Snapping to grid, guides, and other elements' edges/centres, toggleable. | 8d |
| FR-D-10 | Align (6 ways), distribute (2 ways), make-same-size, z-order, group/ungroup, lock. | 8d |
| FR-D-11 | Cut/copy/paste/duplicate within and between open documents; paste-in-place. | 8e |
| FR-D-12 | Layers panel and Object List panel with rename, reorder, show/hide, lock. | 9 |
| FR-D-13 | Property Inspector edits every property of the current selection; multi-select shows common properties with mixed-value indicators. | 9 |
| FR-D-14 | Dragging a data field onto an element binds that element to the field. | 8c |

### 3.2 Object Model & Persistence (FR-M)

| ID | Requirement | Phase |
|---|---|---|
| FR-M-01 | The document model is stored in millimetres and is DPI-independent. | 7a |
| FR-M-02 | Every model mutation goes through the command stack — no direct mutation anywhere in the app. | 7c |
| FR-M-03 | Undo/redo depth configurable (default 100); a continuous drag collapses into one undo step. | 7c/15 |
| FR-M-04 | Templates save to `.lbl` (ZIP: `document.json` + `assets/` + `manifest.json`). | 7d |
| FR-M-05 | Every `.lbl` carries a schema version; older versions migrate forward on open. | 7d |
| FR-M-06 | Save→load→compare round-trip is lossless (deep equality). | 7d |
| FR-M-07 | Recent-files list; dirty-state tracking with save prompt on close. | 7d |
| FR-M-08 | Crash-safe autosave with recovery prompt on next launch. | 17 |

### 3.3 Barcodes & 2D Codes (FR-B)

| ID | Requirement | Phase |
|---|---|---|
| FR-B-01 | 1D symbologies: Code 128 (A/B/C auto), Code 39, EAN-13, EAN-8, UPC-A, UPC-E, ITF-14, Interleaved 2 of 5, GS1-128, Codabar, MSI. | 10a |
| FR-B-02 | 2D symbologies: QR Code, GS1 QR, Data Matrix (square & rectangular), GS1 Data Matrix, PDF417, Aztec. | 10b |
| FR-B-03 | Configurable X-dimension (mil/mm), bar height, wide:narrow ratio, quiet zone. | 10a |
| FR-B-04 | Check digit auto-calculated per symbology, optionally displayed. | 10a/c |
| FR-B-05 | Human-readable interpretation: position (above/below/none), font, custom string. | 10c |
| FR-B-06 | Invalid data for the selected symbology is reported live in the Inspector, not at print time. | 10d |
| FR-B-07 | Warn when the X-dimension is not reproducible at the target printer's DPI. | 10d |
| FR-B-08 | QR: version, ECC level (L/M/Q/H), mask, encoding mode. Data Matrix: size, GS1 FNC1. | 10b |

### 3.4 Data Sources (FR-DS)

| ID | Requirement | Phase |
|---|---|---|
| FR-DS-01 | A document may have multiple named data sources. | 11a |
| FR-DS-02 | Records are exposed as an ordered, typed, cursor-based `RecordSet`; large sets stream rather than fully materialise. | 11a/18 |
| FR-DS-03 | CSV source: file selection, delimiter/quote/encoding detection, header row option, type inference. | 11b |
| FR-DS-04 | Excel source: `.xlsx`/`.xls`, sheet and cell-range selection. | 11b |
| FR-DS-05 | JDBC source: SQL Server and MySQL first-class; others via user-supplied driver JARs. | 11c |
| FR-DS-06 | JDBC: connection wizard, test-connection, schema browser, SQL editor with result preview, parameterised queries. | 11c |
| FR-DS-07 | REST source: URL, method, headers, basic/bearer auth, JSON record-path selector. | 11d |
| FR-DS-08 | Prompt-at-print source: named fields with type, default, validation and optional pick-list, rendered as a form at print time. | 11e |
| FR-DS-09 | Stored credentials are encrypted at rest and never written to the `.lbl` file in plaintext. | 11a |
| FR-DS-10 | Data Sources panel lists sources and their field trees; fields are draggable to the canvas. | 11f |

### 3.5 Variable Data & Expressions (FR-V)

| ID | Requirement | Phase |
|---|---|---|
| FR-V-01 | Any text, barcode or RFID value may mix literal text, `${Field}` references, and expressions. | 12a |
| FR-V-02 | Expression functions: string (concat, substring, upper, lower, trim, pad, replace, regex), numeric, date formatting, number formatting, conditionals, check-digit helpers. | 12b |
| FR-V-03 | Expression evaluation is sandboxed — no arbitrary code execution, no reflection, no I/O. | 12b |
| FR-V-04 | Serial-number fields: start, step, padding, numeric or alphanumeric sequence, rollover. | 12c |
| FR-V-05 | Serial counters persist server-side so concurrent users never produce duplicates. | 12c/21d |
| FR-V-06 | System fields: print date/time, user, machine name, job name, copy number, record number. | 12d |
| FR-V-07 | Canvas shows live merged data with a record navigator (`◀ n of N ▶`). | 12e |

### 3.6 Printing (FR-P)

| ID | Requirement | Phase |
|---|---|---|
| FR-P-01 | Print preview renders the exact output, with page navigation and target-DPI selection. | 13a |
| FR-P-02 | Preview offers 1-bit monochrome simulation matching thermal output. | 13a |
| FR-P-03 | Print to any installed Windows printer with media, copies and orientation selection. | 13b |
| FR-P-04 | Export to PDF (vector, embedded fonts) and PNG/JPEG at a chosen DPI. | 13c |
| FR-P-05 | Direct thermal printing via **ZPL II** (Zebra). | 13e |
| FR-P-06 | Direct thermal printing via **EPL2**, **TSPL** (TSC) and **DPL** (Datamax). | 13f |
| FR-P-07 | Transports: raw TCP:9100, Windows RAW spooler passthrough, serial/USB. | 13d |
| FR-P-08 | Printer profiles store DPI, max width, capabilities, transport and defaults. | 13d |
| FR-P-09 | Elements a command language cannot express are rasterised to a 1-bit image block; the UI reports which elements were degraded. | 13f |
| FR-P-10 | Printed output measures physically correct: a 50 mm design element measures 50 mm ±0.5 mm. | 13/19 |
| FR-P-11 | N-up sheet layout for laser label stock: columns, rows, gaps, start cell. | 13h |
| FR-P-12 | Batch print with record range, copies-per-record, progress and cancel. | 13h |
| FR-P-13 | Per-record error policy: skip, abort, or prompt. | 13h |
| FR-P-14 | Raw-command console and test page per printer profile for diagnostics. | 13d |

### 3.7 RFID (FR-R)

| ID | Requirement | Phase |
|---|---|---|
| FR-R-01 | A document may carry an RFID data block: memory bank, EPC scheme, data binding, lock flag, write power. | 13g |
| FR-R-02 | Encode via the printer's command language (Zebra `^RS`/`^RFW`, TSPL equivalent) as part of the label job. | 13g |
| FR-R-03 | Configurable retry count and void-on-failure behaviour. | 13g |
| FR-R-04 | Written EPC values and encode failures are recorded in print history. | 13g/22 |
| FR-R-05 | The UI blocks RFID configuration for printer profiles without RFID capability. | 13g |

### 3.8 Templates & Library (FR-T)

| ID | Requirement | Phase |
|---|---|---|
| FR-T-01 | Server-backed shared template library with folders, tags, search and thumbnails. | 14 |
| FR-T-02 | Check-out/check-in to prevent concurrent-edit loss. | 14 |
| FR-T-03 | Version history with diff summary and rollback. | 14 |
| FR-T-04 | Local offline cache; templates open when the server is unreachable. | 14/21b |
| FR-T-05 | Built-in stock/page presets plus user-defined custom stock. | 14 |

### 3.9 Multi-user, Security & Audit (FR-S)

| ID | Requirement | Phase |
|---|---|---|
| FR-S-01 | Users authenticate to the server before designing or printing. | 21b |
| FR-S-02 | JWT access + refresh tokens; configurable session lifetime. | 21b |
| FR-S-03 | Optional Windows/LDAP/AD authentication. | 21b |
| FR-S-04 | Offline grace period allows printing with cached credentials when the server is down. | 21b |
| FR-S-05 | Permissions are enforced **server-side**; the client UI only hides what is disallowed. | 21c |
| FR-S-06 | Every print job is logged: user, time, template + version, printer, record range, copies, merged data, RFID EPCs, result. | 22 |
| FR-S-07 | Print history is searchable and exportable; jobs can be reprinted with the original data. | 22 |
| FR-S-08 | Audit trail covers template edits, permission changes, printer changes and login events. | 22 |
| FR-S-09 | Configurable retention policy for history and audit records. | 22 |

### 3.10 Automation & Integration (FR-A)

| ID | Requirement | Phase |
|---|---|---|
| FR-A-01 | Authenticated REST print API accepting template ID, records, printer and options. | 23a |
| FR-A-02 | Job status polling and result retrieval. | 23a |
| FR-A-03 | Integration triggers: watch folder, TCP socket, HTTP endpoint, database poll, schedule/cron, message queue. | 23b |
| FR-A-04 | Transforms: parse CSV/XML/JSON/fixed-width, map and rename fields. | 23b |
| FR-A-05 | Actions: print job, write file, run command, HTTP call, email alert. | 23b |
| FR-A-06 | Visual Integration Builder in the desktop app, with live sample data and a test-run mode. | 23c |
| FR-A-07 | Integrations run in a background Windows service with logging and error handling. | 23b |
| FR-A-08 | CLI for scripted printing. | 23d |

### 3.11 Extensibility, Settings & Shell (FR-X)

| ID | Requirement | Phase |
|---|---|---|
| FR-X-01 | Ribbon UI with contextual tabs and a quick-access toolbar. | 4 |
| FR-X-02 | All commands defined once in an action registry, shared by ribbon, menus, context menus and shortcuts. | 4 |
| FR-X-03 | Dockable, floatable, pinnable panels with persisted layout and named workspaces. | 5 |
| FR-X-04 | Plugin SPI: printer drivers, data sources, element types, expression functions, integration actions. | 16 |
| FR-X-05 | Plugins load from a folder with manifest-declared API version compatibility. | 16 |
| FR-X-06 | Light and dark themes switchable at runtime. | 17 |
| FR-X-07 | User-editable keyboard shortcuts. | 17 |
| FR-X-08 | Per-user settings sync to the server. | 17/21d |
| FR-X-09 | Localisation scaffolding via resource bundles (English first). | 17 |

---

## 4. Non-Functional Requirements

| ID | Requirement | Target | Verified in |
|---|---|---|---|
| NFR-01 | Application cold start to usable window | < 3 s | 18 |
| NFR-02 | Open a typical (< 50 element) template | < 1 s | 18 |
| NFR-03 | Canvas interaction on a 1000-element document | ≥ 60 fps | 18 |
| NFR-04 | Batch merge throughput | ≥ 200 records/s to ZPL | 18 |
| NFR-05 | Batch of 10 000 records | No OOM at 1 GB heap (streaming) | 18 |
| NFR-06 | Server API response (non-print) | p95 < 200 ms | 21 |
| NFR-07 | Dimensional accuracy of printed output | ±0.5 mm over 100 mm | 19 |
| NFR-08 | Barcode scan success at documented minimum X-dimension | 100% on reference scanner | 19 |
| NFR-09 | Client works offline for | ≥ 8 h (configurable grace) | 21b |
| NFR-10 | Installer is silent-installable for IT rollout | `msiexec /qn` | 20 |
| NFR-11 | Credentials at rest | Encrypted, never plaintext on disk | 11a/21 |
| NFR-12 | Expression evaluation | Sandboxed, no code execution | 12b |

---

## 5. Platform & Toolchain

| Item | Decision | Note |
|---|---|---|
| Language level | Java 21 | LTS |
| Build JDK | **JDK 21 LTS** — decided 2026-07-21 | Machine currently has JDK 26 only; JDK 21 to be installed before Phase 2. Enforced by a Maven toolchain so a wrong JDK fails the build loudly. |
| UI | JavaFX 21 | |
| Framework | Spring Boot 3.x | Client (DI/config) and server (web/data/security) |
| Build | Maven multi-module, via `mvnw` | Maven 3.9.9 already cached in `~/.m2/wrapper` |
| Server DB | **SQL Server** (primary), PostgreSQL supported | SQL Server chosen: already a required client data source, so the driver and ops knowledge are present |
| Client DB | H2 file mode | Offline cache, settings, printer profiles |
| Target OS | Windows 10/11 x64 | |
| Packaging | `jpackage` + WiX → MSI | |

---

## 6. Assumptions

- A LabelBuilder server instance is reachable on the LAN; the desktop client is not standalone-only.
- Thermal printers are network-attached (TCP:9100) or shared via a Windows RAW queue.
- Users have write access to `%APPDATA%\LabelBuilder`.
- At least one Zebra (ZPL) and one TSC (TSPL) printer will be available before Phase 13e.

## 7. Open Items

| # | Question | Blocks | Default if unanswered |
|---|---|---|---|
| Q-1 | Exact Zebra/TSC models available for testing; any with RFID? | 13e–13g hardware validation | Develop against Labelary + ZPL spec |
| Q-2 | AD/LDAP SSO required, or app-managed accounts only? | 21b | App-managed accounts; AD as a later add-on |
| Q-3 | Expected concurrent users / labels per day? | 18, 21 sizing | 25 concurrent users, 50 k labels/day |
| Q-4 | Localisation targets beyond English? | 17 | English only, bundles in place |
| Q-5 | Is a printed-label *verification* workflow (scan-back) required? | Out of scope v1 | Out of scope |
| Q-6 | Retention period for print history? | 22 | 7 years (regulated-industry default) |

## 8. Risks

| ID | Risk | Impact | Mitigation |
|---|---|---|---|
| R-01 | No maintained JavaFX ribbon or docking library | Phases 4–5 become build-from-scratch | Keep both minimal and API-small; budget explicitly |
| R-02 | Thermal driver correctness without hardware | Wrong output discovered late | Labelary validation from day one; acquire hardware before 13e |
| R-03 | Screen ↔ print ↔ thermal visual mismatch | User distrust, wasted stock | `Java2DRenderer` is the single reference; PNG-diff regression suite from 6d |
| R-04 | Unsandboxed expressions in a multi-user product | Remote code execution | Whitelisted function registry; never raw SpEL |
| R-05 | Total scope is a 12–18 month single-developer build | Stalls before value delivered | Ship v0.1 at 13c (design + print a static label), then iterate |
| R-06 | Streaming vs. materialising large record sets decided late | Rewrite of the data layer | `RecordSet` is cursor-based from 11a, never `List<Record>` |
| R-07 | ~~Only JDK 26 installed; Spring Boot 3.5 targets ≤ Java 24~~ **Resolved 2026-07-21** | Runtime instability, CGLIB/ASM failures | **Decision: standardise on JDK 21 LTS.** To be installed before Phase 2; a Maven toolchain declaration fails the build on any other JDK |
| R-08 | Credential handling across client, `.lbl` files and server | Security incident | Credentials never in `.lbl`; encrypted at rest; server-side enforcement |

---

## 9. Traceability

Each phase's completion criterion is "the FR IDs listed against it in §3 are demonstrable".
The Phase 19 test suite indexes its tests by FR ID.
