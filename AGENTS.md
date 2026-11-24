# AGENTS.md — DuoFrost

Instructions for coding agents (OpenCode, Claude Code, Codex and similar). Through `opencode.json`
OpenCode also loads **`AGENT_INSTRUCTIONS.md`** (the project's numbered rules and lessons learned,
which take precedence) and `CONTRIBUTING.md`. When you learn a lesson or fix a regression, add it to
`AGENT_INSTRUCTIONS.md`, not here.

## Project

Android app for AYN handhelds (maintainer device: **AYN Thor**) that drives the device LEDs:
screen-matching colours, audio-reactive lighting, custom effects, presets and Smart Scenes.
Independent GPLv3 continuation of BiFrost (Pollux-MoonBench/Bifrost).

- Package / application id: `io.github.tufein.duofrost` (debug adds `.debug`). Do not change it.
- Version: `app/build.gradle.kts`, currently 2.6.0-alpha.4 / 33
- minSdk 33, targetSdk 36, **JDK 17** (`openjdk@17`)
- Language: Kotlin, Gradle Kotlin DSL

## Layout

| Path | What lives there |
| --- | --- |
| `app/src/main/java/io/github/tufein/duofrost/` | App: LED service, screen capture/sampling, scenes, presets, UI |
| `app/src/test/` | JVM unit tests (presets, scenes, plugin catalog, dialog lifecycle, `ScreenSamplingTest`) |
| `app/src/androidTest/` | Instrumented tests; LED behaviour needs a real device |
| `plugins/` | Plugin catalog (`catalog.json`) |
| `docs/` | Per-version user guides, roadmaps, design notes |
| `technical.md`, `CHANGELOG.md` | Technical notes and changelog |

## Build and test

```sh
bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug   # exactly what CI runs
```

Run the release build and its critical lint before publishing.

## Key rules (summary, full list in AGENT_INSTRUCTIONS.md)

- Stability and low overhead over features. Keep power-state updates event-driven.
- Preserve upstream credits and GPLv3; keep fork-specific material out of upstream PRs.
- Keep release signing keys out of Git.
- `SharedPreferences.commit()` calls are deliberate (crash safety); do not "optimise" them to `apply()`.
- Add a test with new logic, or explain in the PR why it cannot be tested off-device. Untested firmware paths stay marked untested.

## Release process

Releases are prereleases created as a **draft** GitHub release first; then
`.github/workflows/upload-duofrost-release.yml` (manual `workflow_dispatch` with the draft `release_id`,
or `repository_dispatch: duofrost-draft-upload`) tests, builds, packages and uploads the APK. Release
notes live in `docs/<version>.md` and `CHANGELOG.md`. The owner always wants work shipped as a
GitHub release with notes that explain what changed in the code.
