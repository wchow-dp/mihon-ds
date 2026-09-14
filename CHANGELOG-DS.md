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

- **"Close companion when leaving the app" now works from anywhere, not just the dashboard.**
  Leaving with a manga open on the companion reset it to the dashboard instead of closing it,
  and leaving from the reader did nothing at all. Two separate causes. Finishing the companion
  clears the active screen, and the main screen treated that clear as a reason to reopen the
  companion — even though the app was on its way out — so it came straight back at the
  dashboard. It only behaved from the dashboard because the active screen was already empty
  there, so nothing changed and nothing reopened. Separately, the reader does not use the
  companion activity at all: it closes it and puts its own window on the second screen, so a
  request aimed at that activity had nothing to act on and the reader stayed up. The companion
  is now asked to close in a way both owners can hear, and the main screen only opens it while
  the app is actually on screen. Returning to the app restores the companion as it was.

### Added

- **Option to keep the page on screen during guided reading.** Settings -> Dual-screen & Tablet ->
  Guided reading. Focusing a panel always centred the panel exactly, which on an axis where the
  whole page already fits meant sliding the page inside its letterbox -- far enough, on real pages,
  to push part of it off the screen edge while empty space opened opposite. Turning this on centres
  the page instead on any axis it already fits; axes where the page is genuinely larger than the
  viewport still pan to the panel as before. Off by default. Measured against 83 recorded focus
  events from a real reading session: 25 of them pushed the page off screen, and none do with this
  enabled.

### Fixed

- **The unit test suite compiles and runs again, and CI runs it.** It had been broken since July:
  `PanelReadingController` gained a `context` parameter its test was never updated for, and the
  v0.20.4 merge added a second break when upstream's `Tracker` grew `getDisplayUsername`. Nothing
  in CI ran the tests, so neither was ever reported. Five tracking assertions were also pinned to
  an older `ChapterUpdate` shape and one to a scroll-sensitivity floor that has since moved; those
  now assert the behaviour rather than the payload. 179 tests pass; two are marked `@Disabled`
  against real, still-unfixed panel sorting bugs rather than quietly deleted.
- **Advanced Recursive no longer reads a tall panel after the shorter ones beside it.** When
  panels overlap enough that no clean cut exists -- a full-height figure with panels bleeding over
  it, which is ordinary manga -- the algorithm falls back to a simple sort, and that sort ranked
  panels by their vertical centre. A panel spanning two rows has its centre *between* them, so it
  was read second, and the taller the panel the further down the order it sank. The fallback now
  ranks by the edge a reader meets first and groups panels whose tops are close into one row,
  ordered across the page in reading direction. Only affects layouts that reach the fallback;
  cleanly separable pages are unchanged.
- **The panel sorting setting no longer labels the wrong option as the default.** "Row-based" was
  marked "(default)" while the preference actually defaults to XY-cut.

### Other

- README now says Webtoon Spanning needs the **Long strip** reading mode. The feature is gated on
  `ViewerType.Webtoon`, which the UI labels "Long strip", so the docs named something that does not
  appear anywhere in the app — and "Long strip with gaps" is a different mode that does not span.

## [0.2.3] - 2026-09-09

### Fixed

- **"Clear layout memory" no longer disappears once you use it.** The row was hidden when no
  layouts were stored, which left an empty "Guided reading" heading behind and hid the
  "No saved panel layouts" text. Preference items in this codebase hide when disabled rather
  than greying out; the row is now always shown and simply does nothing when empty.

### Other

- Repo logo recoloured to match the purple launcher icon published builds use.

## [0.2.2] - 2026-09-09

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

### Added

- **A way to clear Layout Memory.** Corrections are saved implicitly from the reader's
  correction mode and applied silently, so there was no way to see how many were stored or to
  discard them. Settings → Spanning → Guided reading now shows the count and clears them. This
  is also how you remove the entries this release stopped honouring.
- **Option to close the companion display when you leave the app.** The companion runs as its
  own task on the second screen, so pressing home or switching apps left Mihon showing there
  over whatever was now in front. Off by default; enable it in Settings → Spanning. Returning
  to the app brings the companion back. Closing lags the main screen slightly, because Android
  defers the signal until the leave animation finishes.

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
