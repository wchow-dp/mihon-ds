# Reader framing, rotation and guided reading

Notes on why some of the reader code looks the way it does. Written after a run of bugs where
the page ended up in a corner, rotation did nothing, or the two screens disagreed. Each section
is symptom, cause, fix, and what to watch for if you change that area.

This is background, not a spec. `CHANGELOG-DS.md` records what changed for the user.

## Three things fight over the viewport

`SubsamplingScaleImageView` has one scale and one centre, and three separate pieces of code set
them:

1. **`setupZoom`** applies the "zoom start position" preference the moment the image is ready.
2. **`landscapeZoom`** parks a *wide* page against the edge you read from — on a 500 ms delay.
3. **`focusOnPanel`** frames the active panel for guided reading, whenever detection resolves.

They run in that order but finish in a different one, and the last writer wins. Most framing
bugs in this reader are one of them arriving after another and undoing it.

There is a fourth participant that is easy to miss: **the pan limit**. `focusOnPanel` switches
the view to `PAN_LIMIT_CENTER` so a panel hard against an edge can still be centred. That limit
*stays* until something sets it back, and the view is reused across image loads.

### The page sits in a corner

Rotating a page, or just opening a chapter, could leave the whole page squashed into the
bottom-left quadrant with the rest of the screen black.

`focusOnPanel` had already framed a panel and set `PAN_LIMIT_CENTER`. Half a second later the
queued `landscapeZoom` animation fired and drove the centre to `PointF(sWidth, 0)` — the page's
top-right corner. With the normal `PAN_LIMIT_INSIDE` that target is clamped back so the image
still fills the view; under `PAN_LIMIT_CENTER` it is not, so the page slid off into a corner.
Rotation made it obvious because reloading the image re-arms the delay, but it happened on
ordinary page loads too.

Fix: `landscapeZoom` stands down whenever a panel is focused or pending. Checked twice — when
the animation is queued *and* when it fires, because detection usually resolves inside that
500 ms window, so the panel that has to win may not exist yet when the animation is scheduled.
A new image also resets the pan limit rather than inheriting the previous page's.

**If you touch this:** any new code path that moves the viewport needs the same guard, and any
code that sets `PAN_LIMIT_CENTER` owns putting it back.

### The page sits off to one side

Subtler version of the above, with no animation involved. A panel large enough that the fit
scale showed the whole page still had the view centred on *the panel's* midpoint, leaving the
page shoved to one side behind a band of empty background.

`PanelFocusCalculator` takes an `allowOverpan` flag so an edge panel can be centred. That only
makes sense while zoomed in. Once the whole image fits on an axis there is nothing to pan to,
so `clampCenter` now returns the image centre for that axis before the overpan escape hatch is
considered.

## Rotation

Manual rotation is applied in `PagerPageHolder.process()`, not at display time. That is
deliberate: the panel detector runs on whatever `process()` produces, so rotating there means
guided reading re-detects and re-sorts against the rotated layout. Rotating at display time
would leave detection stepping through the upright page's reading order.

Consequences worth knowing:

- Rotation changes the `PanelPageRenderVariant`, which is **part of the detection cache key**.
  A page rotated once has a cached detection result for that variant, so revisiting it can
  activate *immediately*, before the image has finished re-decoding.
- The companion renders whatever variant the page was processed into, so it follows rotation
  for free — as long as nothing downstream second-guesses the variant. It used to hard-code
  `FULL` whenever the panel map was off, which is how the two screens ended up in opposite
  orientations.

### "Show the whole page" must not be a one-shot flag

After rotating, the reader should show the whole page rather than snap into panel one. The
first two attempts at this failed in instructive ways:

- Setting it when the key is pressed misses a rotated page coming back into view.
- Setting it inside `process()` runs it **off the main thread**, and as a one-shot flag it got
  spent on the wrong activation whenever a cached detection result arrived first.

It is now a piece of state on `PanelReadingController` that is held for as long as the rotation
lasts and set on the main thread, from `PagerViewer.applyManualRotation`. Race gone.

### Rotation is cleared when the reader moves on

`onPageChange` clears the rotation once the current page is no longer the rotated one, and
**re-reads the holder next door** — the neighbouring page is still in the pager holding its
rotated bitmap and nothing else would ever ask it to redraw.

A stale comment claimed this already happened long after the code that did it was removed.
If the behaviour changes again, change the comment in the same commit.

## Wide-page settings are portrait-only

"Rotate wide pages to fit" and "Split wide pages" rescue a wide spread on a *tall phone*, where
reshaping it fills more of the screen. Neither checked the shape of the screen it was correcting
for. On a landscape reader they turned spreads sideways and split them apart — and splitting
collapsed panel detection to a single box, leaving guided reading with nothing to step through.

Both are now gated on `viewportIsPortrait()`, read from display metrics because `process()`
runs off the main thread and cannot touch the pager's size.

The gate is itself a setting -- `reshapeWidePagesOnTallScreensOnly`, on by default -- because
whether a spread *should* be reshaped is a preference, not a fact. Turning it off restores the
old always-reshape behaviour. It deliberately does **not** use the shared
`imagePropertyChangedListener`: that refreshes the adapter without rebuilding page views, so the
page in front of the reader keeps its already-decoded image and the setting looks broken. It has
its own listener that forces a full adapter reset, which re-runs the decode that decides a page's
shape.

## Adding a reader action

A new `ReaderAction` needs **three** registrations, and missing any one fails silently with no
error anywhere:

1. the enum itself,
2. `ReaderInputSettingsPage.actionsForLayer()` — otherwise it never appears in the key-binding
   screen and cannot be bound at all,
3. `ReaderActionDispatcher`'s routing set — otherwise a bound key resolves to an action nobody
   handles.

The rotation actions shipped missing (2), then missing (3). Check all three before concluding a
new action "doesn't work".

## The companion presentation's lifecycle

`ReaderPresentation` is its own `LifecycleOwner`, so `lifecycleScope` inside it is *its* scope,
not the activity's. Two consequences worth holding on to:

- **DESTROYED is final.** A `LifecycleRegistry` cannot be moved back up, and the scope is
  cancelled with it. Anything launched from `lifecycleScope` afterwards silently does not run --
  including the page loads and the code that would log their failure. The symptom is a companion
  that is present, on top, unobscured and painting nothing but the dark reader background, with
  an empty log. Teardown therefore belongs to `dismiss()`, not `onDetachedFromWindow()`: a
  dialog's window can be detached and attached again without the dialog ending.
- **Sleep and wake churn the presentation.** Waking the device produced four dismiss/recreate
  cycles in about four seconds before settling. Each cycle cancels any page load in flight; the
  cancellation path has to clear its request marker or the page is never retried.

**Confirmed cause (2026-09-18): a decode finishing while the view is transiently detached.**
The apply-guard used to require the view to be attached at the moment the decode completed. The
Thor's second display flaps -- the system reconfigures it every few seconds when the connection is
unstable -- and each flap detaches the presentation's view for a beat. A decode that landed inside
that beat was discarded, and because the request marker was already stamped, `shouldStartPageLoad`
would not restart it: the page stayed `loaded=null` forever and the screen stayed black. The
instrumented log showed exactly this -- `Companion load start pageIndex=7` followed a second later
by `Companion page not shown ... loaded=null`, during a burst of `Reconfiguring input devices,
changes=DISPLAY_INFO`. The fix drops the attachment requirement from `canApplyPageLoad`: a finished
decode is applied as long as the view still wants that page, and a detached view draws it on
reattach. The key comparison still blocks applying a stale page to a view that has moved on.

When the companion is blank, the log now distinguishes the cases: `Companion load start` means it
asked, `Companion page not shown` means it believed the page was already requested and it never
landed, and silence on both means the scope is dead.

## Testing on a dual-screen device over adb

- **Reader key bindings only match gamepad-source events.** `adb shell input keyevent 96` does
  nothing; `adb shell input gamepad keyevent 96` works. A "the key isn't reaching the app"
  conclusion drawn from plain `input keyevent` is wrong.
- **Check the companion is actually running** before testing anything dual-screen. Look for a
  bare application window (the reader's `Presentation`) or `DualScreenActivity` on the secondary
  display in `dumpsys window windows`. With dual-screen mode off the second screen just shows
  the launcher, and the entire `ReaderPresentation` path goes unexercised.
- `screencap` without `-d` defaults to the *companion* display. Pass the unique id explicitly.
- **A pinned keyboard cannot be dismissed by the app.** The Thor's "IME Pin Mode" (pin icon on
  the keyboard) set to "Pin on the bottom screen" holds the IME on the companion display, and
  `hideSoftInputFromWindow` returns true while the keyboard stays up. `dumpsys input_method` tells
  you which case you are in: `mCurTokenDisplayId` is the display hosting the IME, and `mInputShown`
  is whether the framework thinks it is visible. Hide requests succeeding while `mInputShown` stays
  true means something outside the app is holding it.
- In guided reading the shoulder buttons and d-pad step **panels**, not pages. To change page,
  open the reader menu and use the page slider.
