# Mihon DS 0.2.3

> **Credits.** Mihon DS is not my work. The dual-screen fork was created by
> [mis0suppe](https://github.com/mis0suppe/mihon-ds) and extended by
> [frazse](https://github.com/frazse/mihon-ds) — Layout Memory / manual panel training,
> instant SyncYomi, flicker-free reader transitions and stabilised dual-screen sync are all
> frazse's. Mihon itself is by the [Mihon team](https://github.com/mihonapp/mihon) and its
> contributors. This build only keeps their work current with upstream Mihon.

A follow-up to 0.2.2, fixing the settings entry that release introduced. Still based on
Mihon v0.20.4.

## Fixed

- **"Clear layout memory" no longer disappears once you use it.** The row was hidden whenever
  no layouts were stored, so clearing them made it vanish — leaving an empty "Guided reading"
  heading and hiding the "No saved panel layouts" text meant to replace the count. Preference
  items in this app hide when disabled rather than greying out, which is what the row was
  doing. It now stays visible and simply does nothing when there is nothing to clear.

## Other

- Repo logo recoloured to match the purple launcher icon that published builds use.

## Known issues

Unchanged, all pre-existing:

- `AdaptiveSheet` has a long-standing bug where `context is Presentation` can never be true, so
  secure-flag handling for sheets on the secondary display has never actually engaged.
- The companion window occasionally logs as being on the wrong display; it self-corrects within
  a frame or two.
- The reader's companion-page ("book mode") toggle has no effect in webtoon mode — it applies to
  the paged viewer only. Same behaviour in mis0suppe's original.
- Releases are signed with the auto-generated debug keystore, so they are not reliably
  upgradeable between builds. A real keystore in repo secrets would fix it.
- Closing the companion lags the main screen. Android defers the signal until the leave
  animation finishes, so most of the delay is not ours to remove.

## Testing

Verified on an AYN Thor: with three layouts stored (including one from the old key format),
the setting showed the correct count, clearing emptied the store, and the row stayed visible
afterwards reading "No saved panel layouts".

The Layout Memory ordering fix from 0.2.2 still has not been exercised against a layout trained
on one page and matched on another — the case it exists for.
