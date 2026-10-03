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

## Install

Download `DuoFrost-1.0.0.apk` from the releases page, open it on your Thor and
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
