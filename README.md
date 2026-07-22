# LabelBuilder

A Windows desktop application for designing and printing labels — WYSIWYG designer, barcodes/QR,
RFID encoding, live data binding (SQL Server, MySQL, Excel, CSV, REST), direct thermal printing
(ZPL/EPL/TSPL/DPL), multi-user access control, batch printing, automation and audit logging.

## Documentation

| Document | Contents |
|---|---|
| [docs/SRS.md](docs/SRS.md) | Requirements (FR/NFR), actors, risks |
| [docs/architecture.md](docs/architecture.md) | Module structure, renderer contract, threading rules |
| [docs/lbl-format.md](docs/lbl-format.md) | `.lbl` template file format |
| [docs/api-draft.md](docs/api-draft.md) | Server REST API surface |
| [docs/rbac-matrix.md](docs/rbac-matrix.md) | Roles and permissions |

## Building

Requires JDK 21 registered as a Maven toolchain (`~/.m2/toolchains.xml`). The wrapper supplies
Maven 3.9.9.

```powershell
.\mvnw clean verify          # full build: compile, tests, architecture rules, format check
.\mvnw install               # additionally install jars locally (needed once before -pl builds)
.\mvnw -pl lb-desktop javafx:run   # run the desktop app
.\mvnw spotless:apply        # auto-fix formatting before committing
```

## Modules

```
lb-model       label document model (pure, mm-based, shared client/server)
lb-core        command stack (undo), expressions, .lbl serialisation
lb-barcode     1D (Barcode4J) and 2D (ZXing) symbology facade
lb-render      Java2D reference renderer, PDF export
lb-print       printer driver SPI: ZPL/EPL/TSPL/DPL/Windows + transports, RFID
lb-data        data source SPI: CSV/Excel/JDBC/REST/prompt
lb-plugin-api  stable extension interfaces
lb-automation  trigger → transform → action integration engine
lb-desktop     JavaFX designer application
lb-server      Spring Boot server: auth, RBAC, template library, history
lb-arch        ArchUnit tests enforcing the module dependency rules
lb-dist        jpackage/WiX packaging (no source)
```

The dependency rules between these modules are enforced at build time by `lb-arch`
(see [docs/architecture.md](docs/architecture.md) §2).
