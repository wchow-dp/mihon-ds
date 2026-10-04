# Mihon DS 0.2.4 (release 6)

> **Credits.** Mihon DS is not my work. The dual-screen fork was created by
> [mis0suppe](https://github.com/mis0suppe/mihon-ds) and extended by
> [frazse](https://github.com/frazse/mihon-ds) — Layout Memory / manual panel training,
> instant SyncYomi, flicker-free reader transitions and stabilised dual-screen sync are all
> frazse's. Mihon itself is by the [Mihon team](https://github.com/mihonapp/mihon) and its
> contributors. This build only keeps their work current with upstream Mihon.

> **AI-assisted.** The changes in this fork were made with AI assistance (Claude Code) and
> verified on-device before release.

A big reliability and reading pass over guided reading and the companion display. Still based on
Mihon v0.20.4.

## Read this before updating

This release **updates in place** over the previous signed release (0.2.4 release 5) — no need to
uninstall. (Only the jump *onto* the first signed build required an uninstall.)

## Added

- **Wide spreads are shown whole on a landscape screen.** "Rotate wide pages to fit" and "Split
  wide pages" are portrait-phone rescues; on a landscape reader they turned two-page spreads on
  their side or cut them apart. They are now skipped unless the screen is actually tall, so spreads
  read as drawn. A new setting, **"Only split or rotate on a tall screen"** (on by default), is the
  switch — turn it off to get the old behaviour on any screen.
- **Rotate a page from a bound key.** For the occasional spread printed sideways: cycle a quarter
  turn, or jump straight to 90°, 180° or 270°. Guided reading re-reads the rotated page so its
  panel order stays correct, the companion follows, and the rotation clears when you move on.
- **Leave the app from the companion.** Pressing back or home on the companion now sends the main
  screen to the background too, so both screens leave together. On by default; the mirror of
  "close companion when leaving the app".

## Fixed

- **Restoring a backup no longer closes the app in dual-screen mode.** The file picker opens on the
  companion screen, which the app mistook for you dismissing the companion and backgrounded itself.
  Fixed for the restore picker and the other file pickers (backup folder, library export, storage
  location, colour profile).

- **The companion no longer goes black while reading.** Several distinct causes are fixed: the main
  app and the reader fighting over the second screen, the second display reconfiguring mid-load, the
  companion window being briefly detached, and an interrupted page load never being retried.
- **Opening the app from the companion screen no longer spins.** Launching from the companion's own
  launcher put the main window on the wrong screen and sent it into a restart loop; it now lands the
  reader on the main screen and the dashboard on the companion.
- **Pages are framed centred** instead of drifting into a corner after a page load or a rotation.
- **Panel highlights and the backdrop are independent.** Turning off the backdrop no longer also
  hides the panel highlights, on either screen.
- **The main screen no longer flashes panel numbers while navigating** a page that has a saved
  panel-order correction.
- **The companion follows the main screen's rotation**, instead of the two screens showing opposite
  orientations.
- **Searching dismisses the keyboard more reliably** after you press search or enter. (Note: if your
  device pins the keyboard to the second screen, that is a system setting the app cannot override.)
