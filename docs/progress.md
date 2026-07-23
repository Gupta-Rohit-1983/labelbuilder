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
| 6 — Canvas & rendering | 6a–6e | ⬜ | | |
| 7 — Object model & commands | 7a–7d | ⬜ | | |
| 8 — Editing tools | 8a–8d | ⬜ | | |
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

- Build requires the **JDK 21 toolchain**: Temurin 21.0.11 at `~\.jdks\jdk-21.0.11+10`,
  registered in `~/.m2/toolchains.xml`. On 2026-07-23 that JDK folder was found gutted
  (likely antivirus/disk cleanup) — if the build fails with "No toolchain found", check
  `bin\java.exe` exists there before anything else.
