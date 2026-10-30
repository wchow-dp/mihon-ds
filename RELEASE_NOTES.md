# Mihon DS 0.2.4 (release 7)

> **Credits.** Mihon DS is not my work. The dual-screen fork was created by
> [mis0suppe](https://github.com/mis0suppe/mihon-ds) and extended by
> [frazse](https://github.com/frazse/mihon-ds) — Layout Memory / manual panel training,
> instant SyncYomi, flicker-free reader transitions and stabilised dual-screen sync are all
> frazse's. Mihon itself is by the [Mihon team](https://github.com/mihonapp/mihon) and its
> contributors. This build only keeps their work current with upstream Mihon.

> **AI-assisted.** The changes in this fork were made with AI assistance (Claude Code) and
> verified on-device before release.

Two fixes. Still based on Mihon v0.20.4.

## Read this before updating

This release **updates in place** over 0.2.4 release 6 — no need to uninstall.

## Fixed

- **The dual-screen setting no longer switches itself off.** The app turns dual-screen mode off
  when it can't find a second screen, so a single-screen device isn't left stuck. On the Thor the
  bottom screen can briefly report itself off (asleep, or still waking when Android restarts the
  app), and that turned the setting off for good, so the toggle seemed to untick at random. The
  app now remembers when it was the one that turned the setting off, and turns it back on as soon
  as the second screen is back. A setting you change yourself is left alone.
- **The reader no longer crashes while a chapter's pages are loading or failed to load.** With
  the reader menu open, the page slider was built from a page count of -1 and closed the app. It
  showed up with AllManga, whose chapters can sit without a page list. The main screen takes
  upstream Mihon's fix ([mihonapp/mihon#3941](https://github.com/mihonapp/mihon/pull/3941)); the
  companion screen's slider gets the same guard.

  AllManga itself is now behind a Cloudflare check: if a chapter stalls and then reports
  "Solve captcha in WebView and retry", open the manga's page, tap WebView, pass the check, and
  go back.
