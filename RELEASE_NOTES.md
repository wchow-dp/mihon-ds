# Mihon DS 0.2.1

> **Credits.** Mihon DS is not my work. The dual-screen fork was created by
> [mis0suppe](https://github.com/mis0suppe/mihon-ds) and extended by
> [frazse](https://github.com/frazse/mihon-ds) — Layout Memory / manual panel training,
> instant SyncYomi, flicker-free reader transitions and stabilised dual-screen sync are all
> frazse's. Mihon itself is by the [Mihon team](https://github.com/mihonapp/mihon) and its
> contributors. This build only rebases their work onto a newer upstream Mihon.

Rebases Mihon DS onto **upstream Mihon v0.20.4**. The fork had been sitting on v0.19.4,
so this brings in 300 upstream commits' worth of fixes and features while keeping every
dual-screen capability intact.

## What this build changes

The work in this release is integration, not new features:

- **Rebased Mihon DS onto upstream Mihon v0.20.4** (it had been on v0.19.4). Upstream replaced
  Voyager's `ScreenModel` with androidx `ViewModel` and deleted the `voyager-screenmodel`
  dependency, moved preference classes from function accessors to properties, removed
  `DatabaseHandler` in favour of injecting `Database`, and renamed extension repos to extension
  stores. The fork's dual-screen code was ported across all four changes.
- **Fixed extension repos being wiped on upgrade.** Mihon DS shipped its own database migration
  11, so DS databases never ran upstream's — leaving them without the `extension_store` table.
  Upgrading would either crash or silently discard every configured repo. The migration now
  creates the table and copies the rows across.
- **Fixed the companion-display crash** when reopening a settings screen on the second display.
  This predates the rebase and affects 0.1.6 too.
- **Fixed a stale-navigator leak** in the download queue, introduced while porting it off Voyager.

Since 0.2.0, three companion-display bugs found once sources finally worked:

- **Source filters can be used on the second display.** Selections applied but the screen
  never redrew, so every tap looked like it did nothing. Mihon's filter objects are mutated
  in place and are not Compose state; the main-screen dialog is redrawn by its host, the
  companion screen was not.
- **Checkbox groups stay open.** Ticking a box closed the group, and the ticks only appeared
  after reopening it.
- **No longer crashes when backgrounded with filters open.** The companion filter screen
  carried a FilterList and callbacks, which Android cannot write into the instance-state
  Bundle.

- **Published builds are now `app.mihon.ds.dualscreen` with a purple icon**, so they are not
  mistaken for an official Mihon DS install. Note this is a different package id from 0.2.0,
  so it installs alongside rather than upgrading.

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
  It self-corrects within a frame or two.
- The reader's companion-page ("book mode") toggle has no effect in webtoon mode — it only
  applies to the paged viewer. Same behaviour in mis0suppe's original.
- Releases are still signed with the auto-generated debug keystore, so they are not
  reliably upgradeable between builds. A real keystore in repo secrets would fix it.

## Testing

Verified on an AYN Thor (Android 13, secondary display id 4):

- Upgrading a Mihon DS database in place: schema 12 → 15 with a configured extension repo
  preserved in `extension_store`, `manga_merger` and its indices intact, no errors.
- The companion-display crash fixed here still reproduces on a build of the pre-merge code
  and no longer reproduces on this one.
- Guided reading and panel detection, the reader controls mapper with hardware bindings,
  the download queue opening on the secondary display, and webtoon reading from a real
  source across both panels.

For 0.2.1, on the same device: selecting single-choice filters and ticking several
checkboxes in a group on the companion display, both updating immediately, and the screen
surviving being backgrounded with filters open.

Not exercised: tapping through from the download queue to a manga, and recording a new
control binding.
