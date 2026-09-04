# Mihon DS 0.2.0

Rebases Mihon DS onto **upstream Mihon v0.20.4**. The fork had been sitting on v0.19.4,
so this brings in 300 upstream commits' worth of fixes and features while keeping every
dual-screen capability intact.

## Upstream

Everything in Mihon v0.19.5 through v0.20.4. Highlights: MangaBaka and Hikka tracker
support, extension stores replacing extension repos, filter options on the Updates tab,
`src:` library search, reduced cover-cache memory use, and library updates that run over
a VPN.

## Dual-screen features

All retained: dual-screen mode and the companion dashboard, guided reading and panel
detection, the reader controls mapper, secondary-display scroll sensitivity, webtoon
spanning, tracker progress sync, SyncYomi support, recommendations, and telemetry off
by default.

## Fixes

- **Extension repos survive the upgrade.** Mihon DS databases sit at schema 12 because
  the fork shipped its own migration 11, so they never ran upstream's — meaning no
  `extension_store` table. Upgrading would either crash or silently discard every
  configured extension repo. The migration now creates the table and copies the rows
  across.
- **Companion display no longer crashes** when reopening a settings screen on the second
  display (`Key <screen>:transition was used multiple times`). This bug predates the
  rebase and affects 0.1.6 as well. Screens now swap instantly on the secondary display
  rather than sliding — that animation was the cause.
- **Download-queue navigation** no longer holds a stale navigator after its screen is
  disposed.

## Under the hood

Upstream replaced Voyager's `ScreenModel` with androidx `ViewModel` and dropped the
`voyager-screenmodel` dependency, so the fork's own screen models were ported to
`StateViewModel`. Preference classes moved from function accessors to properties, and
`DatabaseHandler` was removed in favour of injecting `Database` directly. Requires JDK 21
to build.

## Known issues

- `AdaptiveSheet` has a long-standing bug where `context is Presentation` can never be
  true, so secure-flag handling for sheets on the secondary display has never actually
  engaged. Behaviour is unchanged here; the dead branch was removed so the bug is visible.
- `InputDispatcher` occasionally logs that the companion window is on the wrong display.
  It self-corrects within a frame or two. Pre-existing.

## Testing

Verified on an AYN Thor: in-place upgrade, database migration with extension repos
preserved, guided reading, the download queue opening on the secondary display, and the
reader controls mapper with hardware bindings. Webtoon spanning and download-queue
tap-through have not been exercised against a real library.
