# RBAC — Roles & Permission Matrix

**Version:** 0.1 (Phase 1 deliverable; implemented in Phase 21c)
**Last updated:** 2026-07-21

---

## 1. Model

```
User ──< UserRole >── Role ──< RolePermission >── Permission
```

- A user has **many roles**; the effective permission set is the **union** of their roles'
  permissions. There are no "deny" rules — a permission is granted or absent. Deny-overrides makes
  permission reasoning non-local and is a well-known source of security bugs.
- The five roles in §3 are **built-in and seeded**. Administrators may create custom roles from the
  same permission vocabulary (`POST /roles`).
- **Permissions are enforced server-side** (FR-S-05), via Spring Security `@PreAuthorize` on every
  controller method. The desktop client caches the permission set only to grey out UI.

## 2. Permission vocabulary

| Key | Grants |
|---|---|
| `TEMPLATE_VIEW` | List, open and print-preview templates |
| `TEMPLATE_CREATE` | Create new templates |
| `TEMPLATE_EDIT` | Enter design mode; check out, modify and save templates |
| `TEMPLATE_DELETE` | Delete templates |
| `PRINT_EXECUTE` | Submit print jobs, allocate serial numbers, reprint |
| `PRINTER_VIEW` | See printer profiles and select a printer |
| `PRINTER_MANAGE` | Create/edit/delete printer profiles; test page; **send raw commands** |
| `DATASOURCE_VIEW` | See data sources, preview records, read schema |
| `DATASOURCE_MANAGE` | Create/edit/delete data source configs and their credentials |
| `HISTORY_VIEW` | Search and read print history |
| `HISTORY_EXPORT` | Export print history to CSV |
| `AUDIT_VIEW` | Read the audit trail |
| `INTEGRATION_VIEW` | See integration definitions and run logs |
| `INTEGRATION_MANAGE` | Create/edit/enable/disable/test integrations |
| `INTEGRATION_EXECUTE` | Trigger an integration (service accounts) |
| `USER_VIEW` | List users and roles |
| `USER_MANAGE` | Create/edit/disable users; assign roles; reset passwords |
| `ROLE_MANAGE` | Create and modify roles and their permission sets |
| `SETTINGS_MANAGE` | Change server configuration and retention policy |
| `ADMIN` | Reset serial counters and other break-glass operations |

## 3. Built-in roles

`✔` = granted, blank = not granted.

| Permission | Administrator | Designer | Operator | Auditor | Integration Service |
|---|:---:|:---:|:---:|:---:|:---:|
| `TEMPLATE_VIEW`        | ✔ | ✔ | ✔ | ✔ | ✔ |
| `TEMPLATE_CREATE`      | ✔ | ✔ |   |   |   |
| `TEMPLATE_EDIT`        | ✔ | ✔ |   |   |   |
| `TEMPLATE_DELETE`      | ✔ | ✔ |   |   |   |
| `PRINT_EXECUTE`        | ✔ | ✔ | ✔ |   | ✔ |
| `PRINTER_VIEW`         | ✔ | ✔ | ✔ | ✔ | ✔ |
| `PRINTER_MANAGE`       | ✔ |   |   |   |   |
| `DATASOURCE_VIEW`      | ✔ | ✔ |   | ✔ |   |
| `DATASOURCE_MANAGE`    | ✔ | ✔ |   |   |   |
| `HISTORY_VIEW`         | ✔ | ✔ | ✔¹| ✔ |   |
| `HISTORY_EXPORT`       | ✔ |   |   | ✔ |   |
| `AUDIT_VIEW`           | ✔ |   |   | ✔ |   |
| `INTEGRATION_VIEW`     | ✔ | ✔ |   | ✔ |   |
| `INTEGRATION_MANAGE`   | ✔ | ✔ |   |   |   |
| `INTEGRATION_EXECUTE`  | ✔ |   |   |   | ✔ |
| `USER_VIEW`            | ✔ |   |   | ✔ |   |
| `USER_MANAGE`          | ✔ |   |   |   |   |
| `ROLE_MANAGE`          | ✔ |   |   |   |   |
| `SETTINGS_MANAGE`      | ✔ |   |   |   |   |
| `ADMIN`                | ✔ |   |   |   |   |

¹ Operators see **only their own** print history. Scope narrowing is applied in the repository query,
not by a separate permission — see §5.

### Role intent

- **Administrator** — full control. Should be a small number of named accounts, never shared.
- **Designer** — builds templates, wires up data, builds integrations. Cannot manage printers, users
  or roles, and cannot export history.
- **Operator** — the shop-floor role. Opens a template, enters prompt data, prints. **The design
  ribbon is not merely hidden — `TEMPLATE_EDIT` is absent, so a save attempt is refused by the
  server** (this is the concrete demo criterion for Phase 21c).
- **Auditor** — read-only compliance role. Can read everything relevant to what was printed, but
  cannot print, design, or change anything.
- **Integration Service** — non-human. Used by the automation engine and external systems. Deliberately
  cannot design or view credentials.

## 4. Sensitive permissions

Three permissions warrant explicit review because they extend past the application boundary:

| Permission | Risk |
|---|---|
| `PRINTER_MANAGE` | Includes `POST /printers/{id}/raw` — arbitrary bytes to a device on the network. Administrator only. |
| `DATASOURCE_MANAGE` | Holder can point a data source at any reachable database with stored credentials. Designers have it by necessity; consider splitting in a later revision if that proves too broad. |
| `INTEGRATION_MANAGE` | Integration actions include `RUN_COMMAND` — command execution on the integration host. Gated to Administrator and Designer; the `RUN_COMMAND` action specifically may need its own permission before Phase 23 ships. |

## 5. Data scoping

Some permissions grant an *operation* but the visible *rows* are narrowed by ownership:

| Rule | Applies to |
|---|---|
| Operators see only print jobs they submitted | `HISTORY_VIEW` |
| Users may change their own password without `USER_MANAGE` | `POST /users/{id}/password` |
| Template folder ACLs (deferred) | Post-v1 — v1 templates are visible library-wide to anyone with `TEMPLATE_VIEW` |

Scoping is applied in the repository layer so it cannot be bypassed by calling a different endpoint.

## 6. Audit

Every one of these is written to the audit trail (FR-S-08) with actor, timestamp, entity and
before/after values:

- Login success, login failure, logout, token refresh denial
- Template create / edit / delete / restore / check-out / check-in
- Permission and role changes; user create / disable / password reset
- Printer profile changes and every raw-command send
- Data source config changes (values redacted, the fact of change recorded)
- Serial counter resets
- Integration enable / disable / definition change
- Retention policy changes

## 7. Open questions

| # | Question | Default if unanswered |
|---|---|---|
| Q-2 | AD/LDAP group → role mapping, or app-managed roles only? | App-managed; AD mapping as a later add-on |
| Q-7 | Should `RUN_COMMAND` integration actions require their own permission? | Yes — split before Phase 23 ships |
| Q-8 | Are per-folder template ACLs needed? | No for v1; library-wide visibility |
