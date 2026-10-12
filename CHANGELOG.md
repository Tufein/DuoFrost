# DuoFrost changelog

## Next update — in development

- **Smart Scenes fixes:** a rule or temporary choice no longer replaces your own
  preset after a restart, slider changes no longer restart the effect, and a
  scene's brightness limit no longer hides the low-battery alert or charging indicator.
- Starting a capture effect yourself no longer shows the app-profile notification.
- Negative rule priorities stay intact when you edit a rule.

## 2.5.0 — full release

Published on 2026-10-11. This stable release brings together the interface work
from 2.3.0 and the tested 2.5.0 alphas, with final visual and reliability improvements.
Earlier stable releases, including original Version 2, remain available.
Development releases were returned to draft status on 2026-10-12.

- **Polished interface:** a clearer dashboard, preset cards, primary actions and
  filters, with layouts for narrow and wide windows and short landscape screens.
  Searching and organising leave lighting unchanged; tap a preset to apply it
  when manual switching is active.
- **Smart Scenes:** combine apps, game groups, hours, weekdays, battery and
  charging conditions. Choose a preset or apply a brightness-only limit. The
  dashboard explains the active choice; temporary preset holds last 15, 30, 60
  or 120 minutes and re-evaluate current conditions when they end.
- **Preset library:** favourites appear first, named collections combine with
  search, and large libraries load 40 matching cards at a time. Stable preset
  identities preserve new scene links through renames.
- **Effect previews:** see saved colours and supported animations without
  applying lighting or requesting capture. Live-input and flashing effects use
  explained still illustrations; disabled system animations are respected.
- **Import review:** select wanted presets, add separate copies, replace matching
  user presets or skip conflicts. Managed app/plugin presets are protected.
  App assignments are optional and off by default.
- **Temporary undo:** recover one eligible import or deletion for ten minutes,
  including library organisation and app assignments. Later relevant changes
  are preserved, and artwork needed for recovery is retained.
- **Stability and backups:** calmer app/game scene switching, safer handling of
  damaged preset data and fewer repeated settings reads. Profile backups include
  scene rules, app groups, favourites and collections.
- **Final reliability fixes:** failed preset saves show an error and keep the
  saved library intact. Damaged scene data is protected from replacement, and
  invalid restored automation settings no longer crash app detection. Renaming
  preserves Game Scene links, and invalid artwork paths cannot break cleanup.
- **Hidden feature — tidy Recent apps:** the app window stays out of Recent apps;
  reopen DuoFrost from its icon and use Start/Stop to control lighting.

The maintainer reported successful device testing before requesting the full
release. Build and update verification is recorded in [technical.md](technical.md).
Production 2.1.0, 2.3.0 and the 2.5.0 alphas update in place. Original Version 1
and Version 2 need the [one-time backup and reinstall procedure](README.md).

[Release and APK](https://github.com/Tufein/DuoFrost/releases/tag/v2.5.0) ·
[User guide](docs/2.5.0.md) · [Roadmap](docs/roadmap-2.5.0.md).

## 2.5.0-alpha.2 — pre-release

The second testing build toward 2.5.0, initially published on 2026-10-10 and
returned to draft status on 2026-10-12. Its source history and assets are retained;
2.1.0 was the latest stable release when this alpha was published.

- **Preset library:** favourites appear first, named collections work with search,
  and each card has an options menu for previewing, organising and editing.
  Large libraries load 40 matching cards at a time.
- **Effect preview:** view saved colours and supported animations without applying
  lighting or requesting capture. Live-input and flashing effects use explained
  still illustrations; previews respect disabled system animations.
- **Import review:** select individual community presets, add separate copies,
  replace matching user presets while keeping scene links, or skip conflicts.
  Managed app/plugin presets are protected. Importing app assignments is optional
  and off by default; selected assignments merge with existing ones.
- **Temporary undo:** recover one eligible preset import or deletion for ten
  minutes, including library metadata and app assignments. Later relevant edits
  are preserved, and artwork needed for recovery is retained.
- **Backups:** Profiles now includes favourites and collections. Temporary undo
  is excluded and cleared when restoring profiles or images from a full backup.
- **Scene stability:** briefly settle changing app/game context, bound gaps in
  detection and clear context when Usage Access is revoked. Use elapsed time for
  detection caches, reuse unchanged scene data and combine related settings
  refreshes. Temporary-choice countdowns update while visible.

[Historical alpha guide](docs/2.5.0-alpha.2.md) · [Feature research](docs/feature-research-2.5.0.md) ·
[Roadmap](docs/roadmap-2.5.0.md).

## 2.5.0-alpha.1 — pre-release

The first testing build toward 2.5.0, initially published on 2026-10-10 and
returned to draft status on 2026-10-12. Its source history and assets are retained.

- Smart Scenes combine app/game groups, hours, weekdays, battery and charging conditions.
- Brightness-only rules can dim the current preset without replacing it. Higher-priority
  preset rules win, while specific existing app profiles retain precedence.
- User-defined app groups supplement Android's automatic game recognition.
- Temporary preset choices last 15, 30, 60 or 120 minutes; resuming re-evaluates current
  conditions. Stop and reboot clear the temporary choice.
- The dashboard explains the active scene; editing or enabling rules never starts lighting.
- Stable saved preset identities preserve scene links through renames and unambiguous
  managed plugin updates. Appended imports receive distinct identities; older presets
  and external integrations remain compatible.
- Full profile backups include persistent scene rules and groups, excluding temporary holds.

[Historical alpha guide](docs/2.5.0-alpha.1.md) · [Roadmap](docs/roadmap-2.5.0.md).

## 2.3.0 pre-release

An interface update for testing before the next full release. Version 2.1.0 was
the latest stable release when this pre-release was published. The interface
refresh and landscape work prepared as 2.2.0 were combined and published together
as 2.3.0; 2.2.0 remained an unpublished preview. The 2.3.0 release was returned
to draft status on 2026-10-12, retaining its source history and assets.

- A clear lighting dashboard with direct output, mute, timer and capture controls.
- A searchable preset library with readable cards, visible selection and keyboard focus. Browsing or searching never applies a preset.
- Reorganized Lighting, Device, Appearance and Wallpaper pages with larger controls and calmer styling throughout the app.
- Layouts adapt to narrow and wide windows, including short landscape screens. Wide windows place the dashboard and preset library side by side; narrow windows stack them. Unsaved lighting edits and your place in the interface survive screen recreation.
- Scrolling the preset library no longer redraws unchanged card borders on every frame.
- **Hidden feature — tidy Recent apps:** DuoFrost's window stays out of Android's Recent apps list automatically. Reopen it from its app icon; use Start/Stop to turn lighting on or off.

[Historical screenshots and design notes](docs/gui-refresh.md). The production APK uses the 2.1.0 signing key and updates 2.1.0 in place. A debug APK installs separately and is not a production update. For current downloads, use [2.5.0](https://github.com/Tufein/DuoFrost/releases/tag/v2.5.0).

## 2.1.0 fix update

A separate fix update following Version 2, promoted to the latest full release on 2026-10-06. The original Version 1 and Version 2 releases remain available.

**One-time update step:** before uninstalling an older DuoFrost build, export a full backup and save it outside the app. Uninstall the old version, install 2.1.0, import your backup and grant the permissions you need again.

- **Audio fixes:** capture failures stop cleanly without a busy background loop. Audio Reactive and AmbiAurora update both sticks together with fewer hardware calls, preserving separate colors.
- **Preset fixes:** reject invalid or oversized imports, preserve shared preset images and detect unsaved battery/CPU color changes.
- **Custom sleep timer:** choose any whole-number duration from 1 to 120 minutes, or use the existing quick choices. Invalid input or Cancel leaves the current timer intact.
- **Export fixes:** failed backup, theme and preset exports show an error instead of reporting success.

These fixes were checked with automated tests and in an Android emulator. [Download 2.1.0](https://github.com/Tufein/DuoFrost/releases/tag/v2.1.0).

## Version 2

[Original release and downloads](https://github.com/Tufein/DuoFrost/releases/tag/v2). The original signed APK retains Android version 1.5.1.

Version 2 brings all improvements developed after Version 1 together in one public release.

- **Background lighting:** closing DuoFrost or using Clear all can leave lighting running. Added service recovery, capture-resume controls, battery-setting shortcuts and a shareable background status report. Explicit Stop keeps lighting off; schedules take priority over automatic startup.
- **Brightness controls:** adjustable maximum LED output, an optional Battery Saver limit and optional screen-off dimming. Limits apply across effects and app profiles while preserving colors.
- **Sleep timer:** stop lighting after 5, 15, 30, 60 or 120 minutes. Cancel from settings or the ongoing notification.
- **Temporary mute:** mute and unmute from settings, the notification or a home-screen widget without losing the active effect.
- **Home-screen widgets:** Start/Stop and a saved favorite preset for each widget. Favorites update after changes or backup imports.
- **Scheduling:** weekday and weekend selection, overnight rules that follow their starting day, and LEDs-off rules that do not need a preset.
- **Quick Settings:** add the existing on/off tile through Android's own setup prompt.
- **LED test:** six steps to check colors and each stick, with reduced output and automatic restoration of the current effect.
- **Settings backup:** export and restore lighting and background preferences separately from presets and themes, while retaining compatibility with older backups.
- **Control fixes:** more reliable Start/Stop behavior, synchronized visible status, preserved widget/tile actions when opening on Thor's other screen, and settings changes that keep active lighting intact.

Tested by the maintainer on AYN Thor. Android may delay background recovery or a sleep timer while the device is asleep; capture permission may need to be granted again after a process restart. See [technical.md](technical.md) for details.

## Version 1

The first independent DuoFrost release, based on BiFrost.

- Corrected purple and pink hues when ambient saturation boost is enabled.
- Improved custom sampling in single-color mode so vivid colors remain represented.
- Fixed screenshot cleanup and recovery after capture failures.
- Prevented callbacks from an earlier capture session from affecting a new session.
- Established DuoFrost's separate app identity while retaining BiFrost import compatibility, credits and GPLv3 licensing.

BiFrost's existing lighting effects, presets, app profiles, themes and integrations remain the foundation of all DuoFrost releases.
