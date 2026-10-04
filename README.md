# DuoFrost

LED control for the **AYN Thor**, maintained by **Stefan van Den Broek (Tufein)**.
DuoFrost is an independent GPLv3 continuation of [BiFrost](https://github.com/Pollux-MoonBench/Bifrost).

[Download the latest APK](https://github.com/Tufein/DuoFrost/releases/latest) ·
[Report a problem](https://github.com/Tufein/DuoFrost/issues) ·
[Credits](CREDITS.md) · [GPLv3 license](LICENSE)

## Features

- Ambient screen colors, audio-reactive lighting and Ambi Aurora.
- Separate left/right LED control, classic effects and saved presets.
- App-specific profiles, charging/battery indicators and temperature effects.
- Preset, theme and full-backup import/export.
- Optional scheduling, third-party LED control and a repository-owned plugin catalog.
- Weekday/weekend schedules, with overnight rules following their starting day.
- Optional LED dimming during Android Battery Saver and easy Quick Settings tile setup.
- Background lighting after Clear all, safe service recovery, battery settings
  shortcuts and a shareable background status report.
- Adjustable overall/Battery Saver output ceilings and optional screen-off dimming.

## Install

Download `DuoFrost-1.3.0.apk` from the releases page, open it on your Thor and
follow the first-run guide. Enable only the features and permissions you need.
For updates through Obtainium, use:

https://github.com/Tufein/DuoFrost

DuoFrost has its own Android ID, `io.github.tufein.duofrost`, and installs alongside
BiFrost. Run only one LED controller at a time. Existing BiFrost installs cannot
be upgraded directly to DuoFrost; export presets, themes or backups and import
them into DuoFrost. Legacy archive formats remain supported.

Ambient and audio effects need capture access. Screen/audio samples are processed
locally; captured content is not recorded or uploaded by these lighting effects.
Background lighting requires the foreground service to remain active.

## New in 1.3.0

In **Settings → Behavior**:

- **Maximum LED output:** set a 0–100% ceiling across effects, app profiles and
  third-party frames. 0% leaves the service running with its LED output off.
- **Battery Saver limit:** choose 0–100% instead of the fixed 25% cap. Enable
  **Battery Saver LED dimming** to activate it with Android Battery Saver.
- **Dim when screen is off:** optionally use a separate 0–100% ceiling while
  Android puts the device to sleep. Wake restores the other applicable limits.
  Thor lid behavior depends on whether its firmware puts the device to sleep.

The lowest active ceiling wins. Already dim colors stay dim, and changing a
ceiling redraws the current colors without restarting effects or capture. Defaults
preserve previous behavior: overall 100%, Battery Saver 25%, screen-off mode off.
These settings survive app updates and service restoration without modifying
individual presets. Background reports now include the limits and screen/power state.

Install over an earlier DuoFrost release using the same signing certificate.
Physical sleep/wake and LED output need Thor verification; see the
[device checklist](IMPROVEMENTS.md) and [verification](VERIFICATION.md).

## Added in 1.2.0

- **Keep running in background:** enabled by default in Settings → Behavior.
  Closing DuoFrost through Recents or Clear all no longer stops its own LED
  service. Automatic startup after boot is a separate setting.
- **Service recovery:** Android can restore wanted lighting after process
  interruption, including unsaved colors and settings. Stop keeps lighting off.
  Capture needs fresh permission after a process restart; use **Resume
  screen/audio capture** or the notification to grant it.
- **Background controls:** check battery and notification status, request a
  battery optimization exemption, and open the app's Android settings directly.
- **Background report:** share build/device/power status and at most three
  process exit records through Android's chooser. Nothing is sent automatically.
- Schedules take precedence over boot auto-start; installing an update respects
  an explicit Stop. Startup and the tile can fall back to the last configuration
  when there is no saved preset.
- Changing settings applies them within the service, avoiding an interrupted
  stop/start sequence when you close the app during the change.

Install over 1.0.0 or 1.1.0; the Android ID and signing certificate are unchanged.
Allow background battery use on your Thor. If its firmware offers a Recents lock,
you can also use that. Android Force stop requires opening the app again, and
firmware-specific background kills cannot be guaranteed against. See the
[device checklist](IMPROVEMENTS.md) and [verification](VERIFICATION.md).

## Added in 1.1.0

- **Battery Saver LED dimming:** enable it in Settings → Behavior to cap LED
  output at 25% while Android Battery Saver is active. Normal output returns
  automatically, without restarting screen capture. This option is off by default.
- **Schedule weekdays:** choose Monday–Sunday for each rule. A Friday 20:00–07:00
  rule continues into Saturday morning. Existing rules still run every day.
- **Quick Settings:** tap **Add DuoFrost to Quick Settings** in Settings → Behavior
  to add the existing on/off tile through Android's own prompt.
- Rules that turn LEDs off can be created without saved presets.

Version 1.1.0 uses the same signing key as 1.0.0 and updates it in place.
The new controls and physical LED output still need on-device verification.

## First release

Version **1.0.0** includes corrections to purple/pink saturation boosting,
custom single-color sampling, and screenshot resource cleanup and recovery.
See [changes](IMPROVEMENTS.md) and [verification](VERIFICATION.md).

The code builds and passes JVM tests. This release still needs validation on a
physical AYN Thor, including LED output, capture permissions and sleep/wake.
Upstream reported testing on Thor, Odin 2 Portal Pro, Retroid Pocket Mini V2 and
Pocket 5; those reports do not establish hardware validation of DuoFrost.

The plugin catalog starts empty until integrations are verified for DuoFrost's
own API. See [plugin catalog](plugins/README.md) and [integration guide](INTEGRATING.md).

## Build

Use JDK 17 and Android SDK 36:

```sh
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

Release signing is external: see [release instructions](RELEASING.md).
GitHub Actions builds a debug APK and runs regression tests for every push and
pull request. No release signing key is committed or uploaded to GitHub.

## Credits and license

- **Stefan van Den Broek (Tufein):** DuoFrost maintainer and release owner.
- **Pollux / Pollux-MoonBench:** original BiFrost creator.
- **KuriGohan-Kamehameha:** major upstream contributions and system integrations.
- **paradox:** upstream low-battery alert contribution.
- **hupo224:** upstream Retroid Pocket Mini testing and investigation.
- All additional BiFrost contributors remain credited in the preserved Git history.

DuoFrost is modified from upstream BiFrost 1.3.1 (`1baddf1`), with changes made
on 2026-10-03. Original license and authorship are retained. Source and releases
are available at https://github.com/Tufein/DuoFrost. Distributed under GPLv3,
without warranty. DuoFrost is not affiliated with AYN.
