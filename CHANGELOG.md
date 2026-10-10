# DuoFrost changelog

## 2.5.0-alpha.1 — pre-release

The first testing build toward 2.5.0, published as a pre-release on 2026-10-10.

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

[Release and APK](https://github.com/Tufein/DuoFrost/releases/tag/v2.5.0-alpha.1) ·
[Alpha guide](docs/2.5.0-alpha.1.md) · [Roadmap](docs/roadmap-2.5.0.md).

## 2.3.0 pre-release

An interface update for testing before the next full release. Version 2.1.0 remains the latest stable release. The interface refresh and landscape work prepared as 2.2.0 were combined and are published together as 2.3.0; 2.2.0 remained an unpublished preview.

- A clear lighting dashboard with direct output, mute, timer and capture controls.
- A searchable preset library with readable cards, visible selection and keyboard focus. Browsing or searching never applies a preset.
- Reorganized Lighting, Device, Appearance and Wallpaper pages with larger controls and calmer styling throughout the app.
- Layouts adapt to narrow and wide windows, including short landscape screens. Wide windows place the dashboard and preset library side by side; narrow windows stack them. Unsaved lighting edits and your place in the interface survive screen recreation.
- Scrolling the preset library no longer redraws unchanged card borders on every frame.
- **Hidden feature — tidy Recent apps:** DuoFrost's window stays out of Android's Recent apps list automatically. Reopen it from its app icon; use Start/Stop to turn lighting on or off.

[Release and APK](https://github.com/Tufein/DuoFrost/releases/tag/v2.3.0) · [Screenshots and design notes](docs/gui-refresh.md). The production APK uses the 2.1.0 signing key and updates 2.1.0 in place. A debug APK installs separately and is not a production update.

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
