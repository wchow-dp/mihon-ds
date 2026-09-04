<div align="center">

<img src="./.github/assets/logo.svg" alt="Mihon DS logo" title="Mihon DS logo" width="256"/>

# Mihon DS

### Dual-Screen Fork of Mihon
A specialized fork of [Mihon](https://mihon.app) optimized for devices with secondary physical displays (like the AYN Thor, AYANEO Flip DS, and external monitors). This fork is capable of running side-by-side with the official app.

**This repository is a personal build, not original work.** The dual-screen fork was created by
[mis0suppe](https://github.com/mis0suppe/mihon-ds) and substantially extended by
[frazse](https://github.com/frazse/mihon-ds); Mihon itself is by the
[Mihon team](https://github.com/mihonapp/mihon) and contributors. Everything below is their work.
This fork exists only to keep that work current with upstream Mihon.

[![License: Apache-2.0](https://img.shields.io/github/license/mihonapp/mihon?labelColor=27303D&color=0877d2)](/LICENSE)

</div>

## Features

<div align="left">

*   **Dual Screen Support:** Optimized reading experience that spans across two physical displays.
*   **Side-by-Side Installation:** Uses a unique package name (`app.mihon.ds`) so it can be installed alongside the official Mihon app.
*   **Webtoon Spanning:** Automatically synchronizes scrolling across both screens for a continuous webtoon reading experience.
*   **Guided Reading:** Detects panels in paged manga and comics for panel-by-panel navigation with dual-screen context.
*   **Reader Controls Mapper:** Map hardware buttons and controller inputs to reader actions, with global defaults and per-reading-mode overrides.
*   **Secondary Display Scroll Sensitivity:** Adjustable bottom-screen touchpad scroll speed, from 50% to 500% (100% stays one-to-one with finger movement).
*   **Tracker Progress Sync:** Optionally pulls tracker progress into local read status when refreshing entries or manually updating the library.
*   **Customizable Setup:** New onboarding steps to select the target Display ID and rotation overrides.
*   **Privacy Focused:** Telemetry and Crashlytics are disabled by default.

---

### Enhancements by [frazse](https://github.com/frazse/mihon-ds)
Built by [frazse](https://github.com/frazse) on top of [mis0suppe/mihon-ds](https://github.com/mis0suppe/mihon-ds).
They are credited here, not claimed:

*   **Layout Memory (Manual Panel Training):** A "Human-in-the-Loop" solution for panel detection errors. Enter **Correction Mode** in the reader to drag-and-drop panel numbers and fix the reading order. The app calculates a fuzzy "Geometric DNA" for the page and remembers your fix globally across all manga titles.
*   **Instant Synchronization:** Removed the hardcoded 30-second delay for SyncYomi. Synchronization now triggers **instantly** upon app launch, resume, or manual command.
*   **Flicker-Free Reader Transitions:** Eliminated the 1-frame "flash" of the panel overlay during transitions. Visual effects are now hard-blocked and the view hierarchy is cleaned up when Focus Effects are disabled.
*   **Stabilized Dual-Screen Sync:** Unified the rendering logic across both primary and secondary displays to prevent stale layout artifacts on multi-screen devices.

---

### Changes in this build
Integration work only, keeping the above current with upstream:

*   Rebased onto **upstream Mihon v0.20.4** (from v0.19.4), porting the dual-screen code across
    upstream's move from Voyager `ScreenModel` to androidx `ViewModel`, its preference API
    change, the removal of `DatabaseHandler`, and the extension repo → extension store rename.
*   Fixed configured **extension repos being silently discarded** when upgrading a Mihon DS
    database, which sits at a schema version that never ran upstream's migration.
*   Fixed a **crash on the companion display** when reopening a settings screen there
    (pre-existing; affects 0.1.6 as well).

---

*Plus all the standard features of Mihon:*
*   Local reading of content.
*   A configurable reader with multiple viewers, reading directions and other settings.
*   Tracker support: MangaBaka, MyAnimeList, AniList, Kitsu, MangaUpdates, Shikimori, Bangumi, and Hikka.
*   Categories to organize your library.
*   Light and dark themes.

</div>

## Supported Devices

### Dual-Screen Handhelds
Companion page on the secondary display:
*   **AYN Thor**
*   **AYANEO Flip DS**
*   **External Monitors** (via USB-C/HDMI)

### Foldable Devices
Side-by-side view across the hinge:
*   **Microsoft Surface Duo / Duo 2**
*   **Samsung Galaxy Z Fold series**
*   Other devices with Jetpack WindowManager FoldingFeature support

## Installation & Data Sharing

Mihon DS is designed to coexist with the official Mihon app without conflict.

### Storage & Data Sharing
When you first launch Mihon DS, you will be asked to select a storage folder.
*   **Shared Content:** If you select the **same folder** as your main Mihon app, both apps will share the same **Downloads** and **Backups**. This allows you to read your existing library downloads in either app.
*   **Isolated Databases:** Even if you share the storage folder, the **Library Database** (your list of manga, read progress, and categories) remains separate for each app. You can use the Backup/Restore feature to sync your library between them.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## Disclaimer

This is a fork of the [Mihon Open Source Project](https://github.com/mihonapp/mihon). The developer(s) of this fork do not have any affiliation with the content providers available, and this application hosts zero content.

## License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
Copyright © 2024 Mihon DS Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>

</div>
