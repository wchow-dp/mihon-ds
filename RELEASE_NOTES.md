# Mihon DS 0.2.2

> **Credits.** Mihon DS is not my work. The dual-screen fork was created by
> [mis0suppe](https://github.com/mis0suppe/mihon-ds) and extended by
> [frazse](https://github.com/frazse/mihon-ds) — Layout Memory / manual panel training,
> instant SyncYomi, flicker-free reader transitions and stabilised dual-screen sync are all
> frazse's. Mihon itself is by the [Mihon team](https://github.com/mihonapp/mihon) and its
> contributors. This build only keeps their work current with upstream Mihon.

A small release on top of 0.2.1: one Layout Memory correctness fix and one new dual-screen
option. No upstream Mihon changes — still based on Mihon v0.20.4.

## Added

- **Clear Layout Memory.** Panel corrections are saved implicitly and applied silently, so
  there was no way to see how many were stored or to get rid of them. **Settings → Spanning →
  Guided reading** now shows the count and clears them — which is also how you discard the
  entries this release stopped honouring.
- **Option to close the companion display when you leave the app.** The companion runs as its
  own task on the second screen, so pressing home or switching apps left Mihon showing there
  over whatever was now in front. Off by default; enable it in **Settings → Spanning**.
  Returning to the app brings the companion back.

## Fixed

- **Layout Memory no longer scrambles panel order on pages other than the one you trained.**
  A correction was saved as indices into the panel detector's own output order, which is not
  stable from page to page, while the layout signature is built from a geometry-sorted view of
  the same panels. So whenever a signature matched a *different* page — the cross-title case
  the feature exists for — the stored order described an arrangement that did not apply there,
  replacing a usually-correct sort with a meaningless one. Corrections are now recorded against
  the page's geometry, and entries that are not a complete permutation are discarded.

  **Layouts trained on earlier builds need retraining.** Old entries are ignored rather than
  misapplied.

## Known issues

Unchanged from 0.2.1, all pre-existing:

- `AdaptiveSheet` has a long-standing bug where `context is Presentation` can never be true, so
  secure-flag handling for sheets on the secondary display has never actually engaged.
- The companion window occasionally logs as being on the wrong display; it self-corrects within
  a frame or two.
- The reader's companion-page ("book mode") toggle has no effect in webtoon mode — it applies to
  the paged viewer only. Same behaviour in mis0suppe's original.
- Releases are signed with the auto-generated debug keystore, so they are not reliably
  upgradeable between builds. A real keystore in repo secrets would fix it.
- Closing the companion lags the main screen slightly. Android defers the signal until the
  leave animation finishes, so most of the delay is not ours to remove.

## Testing

The Layout Memory fix is reasoned from the code and has not been exercised against a trained
layout on device. The companion-close option was tested on an AYN Thor: it closes on leaving
the app and restores on return.
