# `.lbl` Template File Format — Schema Draft

**Schema version:** 1
**Status:** Draft (Phase 1 deliverable; implemented in Phase 7d)
**Last updated:** 2026-07-21

---

## 1. Container

A `.lbl` file is a **ZIP archive** (stored or deflated) with this layout:

```
mylabel.lbl
├── manifest.json        required — schema version and metadata
├── document.json        required — the label itself
├── thumbnail.png        optional — 256px preview for the template library
└── assets/              optional — embedded binaries
    ├── logo.png
    └── OCRB.ttf
```

A ZIP was chosen over a single JSON file so images and fonts can be **embedded** rather than
referenced by absolute path — a template emailed to another machine must still render.

## 2. `manifest.json`

```jsonc
{
  "schemaVersion": 1,
  "appVersion": "0.1.0",
  "created": "2026-07-21T09:14:22Z",
  "modified": "2026-07-21T11:03:47Z",
  "createdBy": "rohit",
  "modifiedBy": "rohit",
  "thumbnail": "thumbnail.png",
  "assets": [
    { "path": "assets/logo.png", "sha256": "9f2c…", "bytes": 18422 }
  ]
}
```

`schemaVersion` is read **first**, before any other parsing. If it is lower than the current version,
the migration chain (§7) upgrades the JSON before deserialisation. If it is *higher*, the file is
rejected with "created by a newer version of LabelBuilder".

## 3. `document.json` — top level

All coordinates and sizes are in **millimetres**, `double`, origin at the label's top-left.
Angles are degrees clockwise. Colours are `#RRGGBB` or `#RRGGBBAA`.

```jsonc
{
  "id": "9c1e5b8a-...",
  "name": "Carton Label 100x50",
  "stock": {
    "widthMm": 100.0,
    "heightMm": 50.0,
    "orientation": "LANDSCAPE",
    "marginsMm": { "top": 2.0, "right": 2.0, "bottom": 2.0, "left": 2.0 },
    "backgroundColor": "#FFFFFF",
    "presetId": "zebra-4x2"           // null for custom stock
  },
  "targetPrinter": {                   // hint only — drives DPI warnings, never binds the template
    "profileId": "zebra-zt411-203",
    "dpi": 203
  },
  "grid":   { "visible": true, "spacingMm": 1.0, "snap": true },
  "guides": { "vertical": [25.0, 75.0], "horizontal": [10.0] },
  "layers": [
    { "id": "layer-1", "name": "Background", "visible": true, "locked": true },
    { "id": "layer-2", "name": "Content",    "visible": true, "locked": false }
  ],
  "dataSources": [ /* §5 */ ],
  "rfid": { /* §6 */ },                // null when the label has no RFID block
  "elements": [ /* §4 */ ]
}
```

`elements` is ordered **back-to-front**; array index *is* z-order.

## 4. Elements

Jackson polymorphic deserialisation on a `type` discriminator.

### 4.1 Common properties (every element)

```jsonc
{
  "id": "el-7f3a",
  "type": "text",
  "name": "Product Name",
  "layerId": "layer-2",
  "bounds": { "xMm": 5.0, "yMm": 5.0, "widthMm": 60.0, "heightMm": 8.0 },
  "rotation": 0.0,
  "locked": false,
  "visible": true,
  "printCondition": null               // optional expression; element is skipped when it evaluates false
}
```

### 4.2 `text`

```jsonc
{
  "type": "text",
  "value": "SKU: ${Product.Code}",     // literal + ${field} + expressions — see expression syntax
  "font": {
    "family": "Arial", "sizePt": 10.0,
    "bold": false, "italic": false, "underline": false
  },
  "color": "#000000",
  "align": "LEFT",                      // LEFT | CENTER | RIGHT | JUSTIFY
  "verticalAlign": "TOP",               // TOP | MIDDLE | BOTTOM
  "lineSpacing": 1.0,
  "wrap": true,
  "autoFit": "SHRINK_TO_FIT",           // NONE | SHRINK_TO_FIT | GROW_BOUNDS
  "characterSpacing": 0.0
}
```

### 4.3 `barcode` (1D)

```jsonc
{
  "type": "barcode",
  "symbology": "CODE_128",              // §4.9
  "value": "${Product.Code}",
  "xDimensionMm": 0.25,
  "barHeightMm": 12.0,
  "wideToNarrowRatio": 2.5,             // ignored by symbologies that don't use it
  "quietZoneMm": 2.5,
  "checkDigit": "AUTO",                 // AUTO | NONE | PROVIDED
  "hri": {
    "position": "BELOW",                // ABOVE | BELOW | NONE
    "font": { "family": "Arial", "sizePt": 8.0 },
    "showCheckDigit": true,
    "customText": null                  // overrides the encoded value when set
  },
  "rotationQuadrant": 0                  // 0|90|180|270 — thermal firmware only supports quadrants
}
```

### 4.4 `qrcode` (2D)

```jsonc
{
  "type": "qrcode",
  "symbology": "QR",                    // QR | GS1_QR | DATA_MATRIX | GS1_DATA_MATRIX | PDF417 | AZTEC
  "value": "${Product.Url}",
  "moduleSizeMm": 0.5,
  "errorCorrection": "M",               // L | M | Q | H  (QR/Aztec)
  "version": 0,                          // 0 = auto
  "mask": -1,                            // -1 = auto
  "encoding": "AUTO",                   // AUTO | NUMERIC | ALPHANUMERIC | BYTE | KANJI
  "shape": "SQUARE",                    // Data Matrix: SQUARE | RECTANGLE
  "quietZoneModules": 4
}
```

### 4.5 `image`

```jsonc
{
  "type": "image",
  "assetPath": "assets/logo.png",       // relative to the archive root
  "fitMode": "CONTAIN",                 // CONTAIN | COVER | STRETCH | NONE
  "monochrome": {
    "enabled": true,                    // forced true when printing to a 1-bit thermal device
    "method": "THRESHOLD",              // THRESHOLD | FLOYD_STEINBERG | ORDERED
    "threshold": 128
  },
  "opacity": 1.0
}
```

### 4.6 Shapes — `line`, `rectangle`, `ellipse`

```jsonc
{
  "type": "rectangle",
  "stroke":     { "color": "#000000", "widthMm": 0.3, "dash": "SOLID" },  // SOLID|DASH|DOT|DASH_DOT
  "fill":       { "color": "#FFFFFF", "enabled": false },
  "cornerRadiusMm": 0.0                 // rectangle only
}
```

`line` replaces `bounds` semantics: the rectangle's opposite corners define the endpoints, plus
`"lineCap": "BUTT" | "ROUND" | "SQUARE"`.

### 4.7 `table`

```jsonc
{
  "type": "table",
  "columns": [ { "headerText": "Qty", "widthMm": 15.0, "value": "${Line.Qty}" } ],
  "rowSource": "OrderLines",            // data source name; one row per record
  "maxRows": 10,
  "headerRow": true,
  "border": { "color": "#000000", "widthMm": 0.2 },
  "cellPaddingMm": 0.5,
  "font": { "family": "Arial", "sizePt": 8.0 }
}
```

### 4.8 `group`

```jsonc
{ "type": "group", "children": [ /* nested elements, coordinates absolute */ ] }
```

### 4.9 Symbology enum

`CODE_128`, `CODE_39`, `EAN_13`, `EAN_8`, `UPC_A`, `UPC_E`, `ITF_14`, `INTERLEAVED_2_OF_5`,
`GS1_128`, `CODABAR`, `MSI`, `QR`, `GS1_QR`, `DATA_MATRIX`, `GS1_DATA_MATRIX`, `PDF417`, `AZTEC`.

## 5. Data source references

The document stores **which** source to use and its shape — never credentials (FR-DS-09).

```jsonc
"dataSources": [
  {
    "name": "Product",
    "kind": "JDBC",                     // MANUAL | CSV | EXCEL | JDBC | REST
    "configId": "cfg-3a91",             // resolved from the client H2 store or the server
    "query": "SELECT Code, Name, Price FROM Products WHERE Active = 1",
    "parameters": [ { "name": "sku", "boundTo": "${Prompt.SKU}" } ],
    "fields": [                         // cached schema so the designer works offline
      { "name": "Code",  "type": "STRING",  "length": 20 },
      { "name": "Price", "type": "DECIMAL", "scale": 2 }
    ]
  },
  {
    "name": "Prompt",
    "kind": "MANUAL",
    "promptFields": [
      { "name": "SKU", "type": "STRING", "default": "", "required": true,
        "validation": "^[A-Z0-9-]{4,20}$", "pickList": null }
    ]
  }
]
```

A `configId` that cannot be resolved on open produces a "data source not configured" warning; the
template still opens and can be designed, it just cannot merge until reconnected.

### 5.1 Serial number fields

```jsonc
{
  "name": "Serial",
  "kind": "SERIAL",
  "serial": {
    "counterId": "ctr-88fa",            // server-side counter (FR-V-05)
    "start": 1, "step": 1,
    "padTo": 8, "padChar": "0",
    "sequence": "NUMERIC",              // NUMERIC | ALPHANUMERIC | HEX
    "rolloverAt": null,
    "prefix": "SN", "suffix": ""
  }
}
```

## 6. RFID block

```jsonc
"rfid": {
  "enabled": true,
  "memoryBank": "EPC",                  // EPC | USER | TID | RESERVED
  "epcScheme": "SGTIN_96",              // SGTIN_96 | SSCC_96 | RAW_HEX | GID_96
  "value": "${Product.GTIN}${Serial}",
  "dataFormat": "HEX",                  // HEX | ASCII
  "lockAfterWrite": false,
  "writePower": null,                    // null = printer default
  "retryCount": 3,
  "onFailure": "VOID"                    // VOID | SKIP | ABORT
}
```

Rejected at design time if the selected printer profile does not report RFID capability (FR-R-05).

## 7. Versioning & migration

- Current: **schemaVersion 1**.
- Each version bump ships one migration class, `MigrationV{n}ToV{n+1}`, transforming the raw JSON
  tree *before* deserialisation. Migrations chain: a v1 file opened by a v4 app runs 1→2→3→4.
- Never write a giant `switch` on version inside the model classes — that is how the format rots.
- Adding an optional field with a sensible default does **not** require a version bump; Jackson is
  configured with `FAIL_ON_UNKNOWN_PROPERTIES = false` for forward tolerance on minor additions.
- Removing or re-typing a field **does** require a bump plus a migration.

## 8. Invariants (asserted by Phase 7d tests)

1. Save → load → save produces byte-identical `document.json` (stable key ordering, no timestamps
   inside `document.json`).
2. Save → load → deep-equals the in-memory document (FR-M-06).
3. Every `assetPath` referenced by an element exists in the archive, and every asset in the archive
   is referenced by at least one element (no orphans, no dangling links).
4. Every `layerId` on an element exists in `layers`.
5. No credential-shaped value (password, token, connection string with `pwd=`) appears anywhere in
   the archive — asserted by a test that greps the written bytes.
6. All numeric dimensions are finite and non-negative; `widthMm`/`heightMm` are > 0.
