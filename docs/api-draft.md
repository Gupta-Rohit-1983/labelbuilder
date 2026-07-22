# LabelBuilder Server — REST API Surface Draft

**Version:** 0.1 (Phase 1 deliverable; implemented across Phases 21–23)
**Base path:** `/api/v1`
**Auth:** `Authorization: Bearer <JWT>` on everything except `/auth/login` and `/health`
**Content type:** `application/json` unless stated

---

## 1. Conventions

| Aspect | Convention |
|---|---|
| Errors | RFC 7807 `application/problem+json` |
| Paging | `?page=0&size=50&sort=modified,desc`; response wraps `content` + `page` metadata |
| IDs | UUID strings |
| Timestamps | ISO-8601 UTC (`2026-07-21T09:14:22Z`) |
| Concurrency | `ETag` + `If-Match` on template updates |
| Idempotency | `Idempotency-Key` header on `POST /print` — a retried print must not double-print |

Error body:
```json
{ "type": "https://labelbuilder/errors/permission-denied",
  "title": "Permission denied", "status": 403,
  "detail": "Requires TEMPLATE_EDIT", "instance": "/api/v1/templates/9c1e" }
```

**Every endpoint enforces permissions server-side (FR-S-05).** The permission required is listed per
endpoint and maps to [`rbac-matrix.md`](rbac-matrix.md).

---

## 2. Auth — `/auth`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/auth/login` | — | Exchange credentials for tokens |
| POST | `/auth/refresh` | — | Exchange a refresh token for a new access token |
| POST | `/auth/logout` | authenticated | Revoke the refresh token |
| GET | `/auth/me` | authenticated | Current user, roles, and **effective permission set** |

```jsonc
// POST /auth/login
{ "username": "rohit", "password": "…" }
// 200
{ "accessToken": "eyJ…", "refreshToken": "eyJ…", "expiresIn": 3600,
  "user": { "id": "…", "username": "rohit", "displayName": "Rohit",
            "roles": ["DESIGNER"],
            "permissions": ["TEMPLATE_VIEW","TEMPLATE_EDIT","PRINT_EXECUTE", "…"] } }
```

The client caches `permissions` to grey out UI (FR-S-04 offline grace). It is a UX convenience only —
the server re-checks on every call.

---

## 3. Templates — `/templates`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/templates` | `TEMPLATE_VIEW` | List/search — `?q=&folder=&tag=&page=` |
| POST | `/templates` | `TEMPLATE_CREATE` | Create (multipart: metadata + `.lbl`) |
| GET | `/templates/{id}` | `TEMPLATE_VIEW` | Metadata + version list |
| GET | `/templates/{id}/content` | `TEMPLATE_VIEW` | Download the `.lbl` (`application/octet-stream`) |
| PUT | `/templates/{id}` | `TEMPLATE_EDIT` | New version (multipart; requires `If-Match` + check-out) |
| DELETE | `/templates/{id}` | `TEMPLATE_DELETE` | Soft delete |
| GET | `/templates/{id}/thumbnail` | `TEMPLATE_VIEW` | PNG preview |
| POST | `/templates/{id}/checkout` | `TEMPLATE_EDIT` | Lock for editing (FR-T-02) |
| POST | `/templates/{id}/checkin` | `TEMPLATE_EDIT` | Release lock |
| GET | `/templates/{id}/versions` | `TEMPLATE_VIEW` | Version history (FR-T-03) |
| GET | `/templates/{id}/versions/{n}/content` | `TEMPLATE_VIEW` | Download a historical version |
| POST | `/templates/{id}/versions/{n}/restore` | `TEMPLATE_EDIT` | Roll back |

Check-out is advisory but enforced: `PUT` by a user who does not hold the lock returns `409 Conflict`
with the current holder's name.

---

## 4. Printing — `/print`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/print` | `PRINT_EXECUTE` | Submit a print job (FR-A-01) |
| GET | `/print/jobs/{id}` | `PRINT_EXECUTE` or `HISTORY_VIEW` | Job status |
| POST | `/print/jobs/{id}/cancel` | `PRINT_EXECUTE` | Cancel a running job |
| POST | `/print/preview` | `PRINT_EXECUTE` | Render without printing → PDF or PNG |

```jsonc
// POST /print
{
  "templateId": "9c1e5b8a-…",
  "templateVersion": null,              // null = latest
  "printerProfileId": "prn-zebra-01",
  "copies": 1,
  "data": {
    "mode": "INLINE",                   // INLINE | DATA_SOURCE
    "records": [ { "Code": "ABC-123", "Name": "Widget" } ]
  },
  // when mode = DATA_SOURCE:
  // "dataSourceName": "Product", "recordRange": { "from": 1, "to": 200 },
  "options": {
    "errorPolicy": "SKIP",              // SKIP | ABORT
    "nUp": null,
    "rfid": { "verify": true }
  }
}
// 202 Accepted
{ "jobId": "job-1f2e", "status": "QUEUED", "recordCount": 1,
  "statusUrl": "/api/v1/print/jobs/job-1f2e" }
```

`202` not `200`: printing is asynchronous. Status transitions
`QUEUED → RENDERING → SENDING → COMPLETED | FAILED | CANCELLED | PARTIAL`.

```jsonc
// GET /print/jobs/job-1f2e
{ "jobId": "job-1f2e", "status": "PARTIAL",
  "submitted": "2026-07-21T09:14:22Z", "completed": "2026-07-21T09:14:31Z",
  "recordCount": 200, "printed": 198, "failed": 2,
  "failures": [ { "recordIndex": 44, "reason": "RFID encode failed after 3 retries" } ] }
```

---

## 5. Printer profiles — `/printers`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/printers` | `PRINTER_VIEW` | List profiles |
| POST | `/printers` | `PRINTER_MANAGE` | Create |
| PUT | `/printers/{id}` | `PRINTER_MANAGE` | Update |
| DELETE | `/printers/{id}` | `PRINTER_MANAGE` | Delete |
| POST | `/printers/{id}/test` | `PRINTER_MANAGE` | Send a test page |
| POST | `/printers/{id}/raw` | `PRINTER_MANAGE` | Send raw commands (diagnostics, FR-P-14) |

```jsonc
{ "id": "prn-zebra-01", "name": "Warehouse Zebra 1",
  "driver": "ZPL", "dpi": 203, "maxWidthMm": 104.0,
  "transport": { "kind": "TCP", "host": "10.0.4.21", "port": 9100 },
  "capabilities": { "rfid": true, "cutter": true, "peeler": false,
                    "symbologies": ["CODE_128","QR","DATA_MATRIX", "…"] },
  "defaults": { "darkness": 12, "speedIps": 4 } }
```

`POST /printers/{id}/raw` is deliberately gated behind `PRINTER_MANAGE` — it can send arbitrary
bytes to a device on the network.

---

## 6. Data source configs — `/datasources`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/datasources` | `DATASOURCE_VIEW` | List (secrets **never** returned) |
| POST | `/datasources` | `DATASOURCE_MANAGE` | Create |
| PUT | `/datasources/{id}` | `DATASOURCE_MANAGE` | Update |
| DELETE | `/datasources/{id}` | `DATASOURCE_MANAGE` | Delete |
| POST | `/datasources/{id}/test` | `DATASOURCE_MANAGE` | Test connection |
| POST | `/datasources/{id}/preview` | `DATASOURCE_VIEW` | First N records for the designer |
| GET | `/datasources/{id}/schema` | `DATASOURCE_VIEW` | Field list |

Passwords are write-only: accepted on `POST`/`PUT`, stored encrypted, and returned as
`"password": null` with a `"hasPassword": true` flag.

---

## 7. Serial counters — `/serials`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/serials/{counterId}/allocate` | `PRINT_EXECUTE` | Reserve a block of numbers |
| GET | `/serials/{counterId}` | `TEMPLATE_VIEW` | Current value |
| PUT | `/serials/{counterId}` | `ADMIN` | Reset (audited) |

```jsonc
// POST /serials/ctr-88fa/allocate  { "count": 200 }
// 200
{ "counterId": "ctr-88fa", "from": 10401, "to": 10600 }
```

Allocation takes a row lock so concurrent clients cannot collide (FR-V-05). Blocks are allocated up
front so offline printing stays unique — an abandoned block leaves a gap, which is correct and
preferable to a duplicate.

---

## 8. Print history & audit — `/history`, `/audit`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/history` | `HISTORY_VIEW` | Search jobs — `?user=&template=&printer=&from=&to=&status=` |
| GET | `/history/{jobId}` | `HISTORY_VIEW` | Full detail incl. merged data snapshot |
| GET | `/history/{jobId}/records` | `HISTORY_VIEW` | Per-record results and RFID EPCs |
| POST | `/history/{jobId}/reprint` | `PRINT_EXECUTE` | Reprint with the original data (FR-S-07) |
| GET | `/history/export` | `HISTORY_EXPORT` | CSV export |
| GET | `/audit` | `AUDIT_VIEW` | Audit events — `?actor=&entity=&from=&to=` |

History and audit records are **append-only**. There is no update or delete endpoint; expiry is
handled by the server-side retention job (FR-S-09), not by API callers.

---

## 9. Users, roles, permissions — `/users`, `/roles`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/users` | `USER_VIEW` | List |
| POST | `/users` | `USER_MANAGE` | Create |
| PUT | `/users/{id}` | `USER_MANAGE` | Update (roles, enabled) |
| POST | `/users/{id}/password` | `USER_MANAGE` or self | Change password |
| DELETE | `/users/{id}` | `USER_MANAGE` | Disable |
| GET | `/roles` | `USER_VIEW` | List roles + their permissions |
| POST | `/roles` | `ROLE_MANAGE` | Create custom role |
| PUT | `/roles/{id}` | `ROLE_MANAGE` | Update permission set |
| GET | `/permissions` | authenticated | Enumerate all permission keys |

---

## 10. Integrations — `/integrations`  *(Phase 23)*

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/integrations` | `INTEGRATION_VIEW` | List |
| POST | `/integrations` | `INTEGRATION_MANAGE` | Create definition |
| PUT | `/integrations/{id}` | `INTEGRATION_MANAGE` | Update |
| DELETE | `/integrations/{id}` | `INTEGRATION_MANAGE` | Delete |
| POST | `/integrations/{id}/enable` | `INTEGRATION_MANAGE` | Start |
| POST | `/integrations/{id}/disable` | `INTEGRATION_MANAGE` | Stop |
| POST | `/integrations/{id}/test` | `INTEGRATION_MANAGE` | Dry run with sample data (FR-A-06) |
| GET | `/integrations/{id}/runs` | `INTEGRATION_VIEW` | Execution log |
| POST | `/integrations/{id}/trigger` | `INTEGRATION_EXECUTE` | HTTP-trigger entry point |

```jsonc
// Integration definition shape
{ "name": "ERP carton labels", "enabled": true,
  "trigger": { "kind": "WATCH_FOLDER", "path": "\\\\erp\\out\\labels",
               "filePattern": "*.csv", "pollSeconds": 5 },
  "transform": { "kind": "CSV", "delimiter": ",", "hasHeader": true,
                 "fieldMap": { "ItemCode": "Code", "Desc": "Name" } },
  "actions": [
    { "kind": "PRINT", "templateId": "9c1e…", "printerProfileId": "prn-zebra-01", "copies": 1 },
    { "kind": "MOVE_FILE", "to": "\\\\erp\\out\\labels\\done" },
    { "kind": "EMAIL_ON_ERROR", "to": ["ops@example.com"] }
  ] }
```

---

## 11. System — `/health`, `/info`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/health` | — | Liveness/readiness (Actuator) |
| GET | `/info` | authenticated | Server version, schema version, min supported client version |

`/info` carries `minClientVersion` so an out-of-date desktop client is told to upgrade rather than
failing on an unknown payload shape.

---

## 12. Deferred to a later revision

- WebSocket/SSE push for live print-job progress (polling is adequate for v1).
- Bulk template import/export.
- Webhooks on job completion.
- OpenAPI-generated client SDK (the spec is published from Phase 21a; codegen is optional).
