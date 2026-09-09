# Mihon DS fork changelog

Changes made in this fork on top of [frazse/mihon-ds](https://github.com/frazse/mihon-ds).
Upstream Mihon's own changelog is in `CHANGELOG.md`; this file covers only what this fork
ports, fixes or adds. Newest first.

Categories follow upstream's convention: `Added`, `Changed`, `Improved`, `Removed`,
`Fixed`, `Other`.

## How this file is kept

- Every change lands with an entry under `## [Unreleased]`, in the same commit as the change.
- When a release is cut, rename that heading to the version and date, and rewrite
  `RELEASE_NOTES.md` — CI publishes that file verbatim as the release body, so it describes a
  single release, while this file keeps the history.
- Anything that changes what the app *does* for a user also gets a line in the README's
  "Changes in this fork" section. Internal fixes stay here only.
- Entries say what changed and why it mattered, not which files moved.

## [Unreleased]

### Fixed

- **Layout Memory no longer scrambles panel order on matched pages.** Corrections were
  persisted as indices into the raw detector output, while the layout signature is built from
  a geometry-sorted view of the same panels. Raw detection order is not stable between pages,
  so whenever a signature matched a page other than the one it was trained on — the
  cross-title case the feature exists for — the stored order described a different arrangement
  and replaced a usually-correct sort with a meaningless one. Orders are now stored and read
  against the same canonical (top, then left) ordering used to build the signature, stored
  entries that are not a complete permutation of the page's panels are discarded, and the key
  prefix moved to `FUZZY_V3` so existing entries are ignored rather than misapplied.
  Layouts trained before this build need retraining.

### Other

- `PanelCorrectionStore.size()` and `clearAll()`, groundwork for a "clear layout memory"
  setting. Not reachable from the UI yet.

## [0.2.1] - 2026-09-04

### Fixed

- **Source filters work on the companion display.** Selections applied but the screen never
  redrew, so every tap looked like it did nothing. Mihon's filter objects are mutated in place
  and are not Compose state; the main-screen dialog is redrawn by its host, the companion
  screen was not.
- **Checkbox groups stay open** while ticking, instead of closing and only showing the ticks
  after being reopened.
- **No crash when backgrounded with the companion filter screen open.** That screen carried a
  `FilterList` and callbacks, which Android cannot write into the instance-state bundle.

### Changed

- Published builds use the `app.mihon.ds.dualscreen` package id and a purple icon, so they
  are not mistaken for an official Mihon DS install. This installs alongside 0.2.0 rather than
  upgrading it.

## [0.2.0] - 2026-09-03

### Changed

- **Rebased onto upstream Mihon v0.20.4**, from v0.19.4 — roughly 300 upstream commits, with
  every dual-screen capability kept. Upstream had replaced Voyager's `ScreenModel` with
  androidx `ViewModel` and dropped `voyager-screenmodel`, moved preference classes from
  function accessors to properties, removed `DatabaseHandler` in favour of injecting
  `Database`, and renamed extension repos to extension stores; the fork's dual-screen code
  was ported across all four.

### Fixed

- **Extension repos were wiped on upgrade.** Mihon DS shipped its own database migration 11,
  so DS databases never ran upstream's and had no `extension_store` table. Upgrading either
  crashed or silently discarded every configured repo. The migration now creates the table and
  copies the rows across.
- **Companion-display crash** when reopening a settings screen on the second display. Predates
  the rebase; affects 0.1.6 too.
- **Stale-navigator leak** in the download queue, introduced while porting it off Voyager.
