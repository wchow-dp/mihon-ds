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

### Added

- **Searching asks the keyboard to close more thoroughly.** Pressing search or enter could leave
  the keyboard up. The request now goes through the input method manager as well as the Compose
  keyboard controller, since the latter can be absent and the safe call then swallows the request
  silently; settings search hides the keyboard rather than only dropping focus.

  **This does not help when the device pins the keyboard to a second screen.** On the AYN Thor,
  "IME Pin Mode" (the pin icon on the keyboard) set to "Pin on the bottom screen" holds the
  keyboard on the companion display and no app can dismiss it: the framework accepts the hide --
  `hideSoftInputFromWindow` returns true, twice, a frame apart -- and the keyboard stays regardless.
  Switching that setting to "Auto display" moves the keyboard to the main screen, where hiding
  works normally. That is a trade-off for the reader to make, not something the app can decide.
- **Wide pages are no longer reshaped on a landscape screen.** "Rotate wide pages to fit" and
  "Split wide pages" exist to rescue a wide spread on a *tall* phone, where rotating or splitting
  it fills more of the screen. Neither checked the shape of the screen it was correcting for, so on
  a landscape reader they turned spreads sideways and split them apart -- and splitting collapsed
  panel detection to a single box, leaving guided reading with nothing to step through. Both are
  now skipped when the viewport is wider than it is tall. The settings keep their value and still
  apply on a portrait viewport.
- **The companion follows the main screen's rotation.** It rendered the page unrotated whenever
  the panel map was off, so rotating a page left the two screens showing opposite orientations. It
  now uses the variant the page was actually rendered with.
- **Rotating fits the whole page rather than jumping into a panel.** The zoom and centre held for
  the previous orientation were being applied to the freshly rotated image, leaving the page small
  and pushed into a corner, and detection then snapped straight to the first panel. Rotating now
  drops that focus and lands on no active panel, so the rotated page is shown whole; stepping
  forward enters the panels of the new layout.
- **Rotate page, bindable to a key.** Some spreads are published sideways, and on a landscape
  handheld they are both unreadable and small. Four new reader actions: "Rotate page (cycle)" steps
  a quarter turn at a time, and 90°, 180° and 270° each go straight to that angle, since a series
  published sideways stays that way and cycling past the others every time is wasted presses.
  Pressing an angle it is already at puts the page back upright, so one key both applies and undoes.
  The rotation is dropped as soon as you move to another page,
  so it never leaks into normal reading. Rotation is applied where the page is processed rather
  than at display time, which means the panel detector sees the rotated image: guided reading
  re-detects and re-sorts against the rotated layout instead of stepping through the upright page's
  order. The companion follows, since it already renders whatever variant the page was processed
  into.
- **"Only split or rotate on a tall screen", a setting.** Splitting and rotating wide pages exist to
  rescue a spread on a tall phone; on a landscape reader they turn spreads on their side or cut them
  in half. That is now skipped automatically, and this setting is the switch for it -- on by default,
  so wide spreads are shown as drawn. Turning it off reshapes on any screen, which is how both
  options behaved before. Shown under the two options it governs, and only while one of them is on.
  Changing it redraws the page you are looking at rather than waiting for the next one.
- **Option to leave the app when you close the companion.** Pressing back or home on the companion
  display dismissed only the companion, leaving Mihon up on the main screen -- the physical buttons
  act on whichever display has focus, so the primary one never heard about it. Both screens now
  step back together: the main screen is sent to the background, so the app stays in recents and
  returning picks up where it left off. The exact mirror of "close companion when leaving the app",
  and on by default. Nothing about the buttons themselves changes: back still returns to the
  companion's dashboard from inside a screen, and still dismisses the companion from the dashboard,
  with Android's own handling and animation. The main screen simply reacts to the companion going
  away.

### Fixed

- **Pages are framed centred instead of drifting into a corner.** Opening a chapter, or rotating a
  page, could leave the whole page squashed into one corner of the screen with the rest black.
  "Landscape zoom" parks a wide page against the edge you read from, half a second after the image
  loads -- by which time guided reading has framed a panel, and framing a panel loosens the pan
  limit so that edge target is no longer held inside the image. Landscape zoom now stands down
  while a panel is focused. Separately, a panel large enough to show the whole page still centred
  on the panel rather than the page, leaving it shoved to one side; once the page fits on screen it
  is now centred on the page.
- **A rotated page is forgotten once you move on.** Rotation was meant to be dropped when the
  reader leaves the page, so it never leaks into normal reading. Nothing actually dropped it, so a
  page rotated earlier was still sideways when you came back to it. It is now cleared on page
  change, and the neighbouring page -- still held in the pager with its rotated image -- is redrawn
  rather than left sideways.

- **The companion no longer stays black after a page load is interrupted.** The companion stamps a
  "load requested" marker on its view before fetching the page, and the check that decides whether
  to start a load reads that marker as "already handled". A cancelled load left the marker in
  place, so the page was never retried and the second screen stayed blank until the page changed --
  while the reader carried on normally, which is why it looked like the display had died rather
  than a load having failed. Nothing appeared in the log either: that failure path logged at DEBUG,
  and release builds drop anything below INFO. The marker is now cleared when a load is cancelled
  or fails, cancellation is rethrown instead of swallowed, and the failure logs at WARN. Observed
  in the wild on 0.2.4, where a cancelled load left the companion black for eleven minutes.

## [0.2.4] - 2026-09-14

### Changed

- **Dual-screen mode and "Close companion when leaving the app" are on by default.** Only affects
  fresh installs, since an existing choice is never overwritten. Dual-screen mode being off out of
  the box meant a new install of a dual-screen build behaved like stock Mihon until you found the
  setting; MainActivity still turns it back off at startup when no secondary display is present,
  so a single-screen device is not left in a broken state.

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

### Fixed

- **The unit test suite compiles and runs again, and CI runs it.** It had been broken since July:
  `PanelReadingController` gained a `context` parameter its test was never updated for, and the
  v0.20.4 merge added a second break when upstream's `Tracker` grew `getDisplayUsername`. Nothing
  in CI ran the tests, so neither was ever reported. Five tracking assertions were also pinned to
  an older `ChapterUpdate` shape and one to a scroll-sensitivity floor that has since moved; those
  now assert the behaviour rather than the payload. 182 tests pass; two are marked `@Disabled`
  against real, still-unfixed panel sorting bugs rather than quietly deleted.
- **The companion no longer goes black mid-read.** Rebuilding the reader's second-screen window
  first tells the companion activity to close, so it is not squatting on that display. It is
  singleInstance, so when it exists the request reaches it and it finishes; when it does not --
  the normal case while reading -- Android creates one purely to run that finish. That throwaway
  activity carried no launch display, so it appeared on the *primary* screen on top of the reader
  and stopped it. On resume the reader saw a second-screen window that was no longer showing and
  rebuilt it, which sent the request again: a loop that ran about once a second and left the
  companion black, because its window was destroyed and recreated before it could draw. The
  request now goes to the secondary display, where it finishes without disturbing the reader.
- **Guided reading frames the right part of the page when border cropping is on.** Panels are
  detected against the original image file, but the paged viewer was displaying that page with its
  borders cropped -- a different, smaller picture -- and nothing translated the panel positions
  between the two. Every panel was framed against a page that no longer existed, drifting further
  off the further down the page you read: on a 1200x1752 page trimmed to 1115x1532, the focused
  panel sat about 150px above where it belonged. The companion display was unaffected because it
  already renders uncropped, which is why its highlight looked correct while the main screen did
  not. Cropping is now suppressed while guided reading is active, so both screens and the detected
  panels share one coordinate space, and the Crop borders setting greys out to say so rather than
  silently doing nothing. Turning guided reading off restores cropping immediately.
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

- **The signing keystore secret tolerates whitespace.** Kotlin's Base64 rejects every character
  outside its alphabet, so a trailing newline from `base64 ... | pbcopy` failed the build with
  "Symbol '\n' is prohibited after the pad character", and a value soft-wrapped somewhere in
  transit failed with "Invalid symbol ' ' at index 6". Neither message mentions signing, so both
  read like a corrupt keystore. Whitespace carries no meaning in base64 and is now stripped.
- **Releases can now be signed with a real keystore.** CI previously fell back to a debug key
  generated fresh on each runner, so every release was signed differently and Android refused to
  update one over another — each new version meant uninstalling, losing the library, and restoring
  a backup. The build already supported signing via `MIHON_GITHUB_RELEASE`; the workflow just never
  passed the secrets. Needs `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`
  and `SIGNING_KEY_PASSWORD` in repo secrets; without them the build still works and warns.
  The first signed release still requires one last uninstall, since it changes the signature.
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
