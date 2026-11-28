# DuoFrost roadmap

Status on 2026-11-28. This plan describes what the repository shows today, what is
verified, and what still needs a physical AYN Thor. It contains no release dates.
Decisions the maintainer must make are listed first, so work does not stall on them.

## Where things stand

| Item | State |
| --- | --- |
| Latest public release | [2.5.1](https://github.com/Tufein/DuoFrost/releases/tag/v2.5.1), published 2026-10-15. Startup dialog fix for the AYN Thor |
| `main` | 2.6.0-alpha.4, versionCode 33. A draft test build, not a public release |
| 2.6.0 stable | [PR #19](https://github.com/Tufein/DuoFrost/pull/19), versionCode 34. Open and not merged. Waits for the signed APK and the Thor checks |
| Dependabot | PRs #9 to #18 are open: GitHub Actions, androidx, the Gradle wrapper and the Android build plugin |
| Other drafts | [PR #20](https://github.com/Tufein/DuoFrost/pull/20) adds `.mailmap`. [PR #21](https://github.com/Tufein/DuoFrost/pull/21) adds OpenCode instructions |
| Screen readout (v3) | Design note in [docs/screen-readout-v3.md](screen-readout-v3.md). The `ScreenReadoutProbe` experiment is merged (PR #7). The probe result from the Thor is not recorded yet |

## Decisions for the maintainer

1. **Signing key for 2.6.0.** [docs/2.6.0.md](2.6.0.md) in PR #19 says 2.6.0 is signed
   with a new release key, so users must export a backup, uninstall, install and import.
   Rules 30 and 39 in [AGENT_INSTRUCTIONS.md](../AGENT_INSTRUCTIONS.md) say upgrades keep
   the same key. Confirm the choice, then update those rules so they match.
2. **Pre-release policy.** [roadmap-2.5.0.md](roadmap-2.5.0.md) says only stable releases
   are published. The `v2.6.0-alpha.4-main` source pre-release was published on
   2026-11-23. Keep it as a pre-release, or change it back to a draft?
3. **Drafts #20 and #21.** `.mailmap` only changes how Git shows author names. PR #21
   adds `AGENTS.md`, which overlaps `AGENT_INSTRUCTIONS.md`. Choose one instruction file
   for agents before merging it.
4. **Dependabot major versions.** The Android Gradle Plugin update (#12) and the Gradle
   9.9.1 wrapper (#18) change the build toolchain. Merge them only after the release
   build and lint pass on the maintainer's machine. Action bumps can merge once CI is
   green on each PR.

## Next steps

### 1. Ship 2.6.0 (maintainer, on a local machine)

- Settle decision 1.
- Run the unit tests, `assembleRelease` and `lintRelease` with JDK 17. Sign the APK with
  the release key. The key stays outside Git and the cloud.
- Test on an AYN Thor: fresh install, backup export and import, the theme editor,
  preset transitions, the scene simulator, the onboarding LED checks and the screen
  readout test. Record the firmware and each check that was actually run in
  `docs/2.6.0.md`, replacing the placeholder.
- Merge PR #19. Create the draft release, upload the APK and checksums, verify the
  digests, then publish the stable release.

### 2. Keep the PR queue small

- Triage the Dependabot PRs each week, following decision 4.
- Close or merge #20 and #21 after decision 3.

### 3. Screen readout v3 (gated by measurements)

Done so far:

- The design note and the feasibility plan in `docs/screen-readout-v3.md`.
- Thor measurements from 2026-11-03 over ADB. A full-frame `screencap` takes about
  0.11 s and produces about 5.3 MB, so whole frames are too heavy for 60 fps.
- The one-tap `ScreenReadoutProbe` in the app.

Still open:

- Which user `PServerBinder` runs commands as, and whether its output can reach the app.
  The probe reports this on the Thor. Record the result in `docs/screen-readout-v3.md`
  before any code depends on it.

Work that is safe to build now, without a privileged source:

- Extract a `ColorSource` interface from `ScreenAnalyzer`. Existing behaviour and unit
  tests stay unchanged (plan step 1).
- Adaptive frame rate for the MediaProjection source. Pause while the screen is off or
  static (plan step 3). Measure battery per profile before and after. This needs the
  device.

Gate: no privileged source (`PServerBinder` or Shizuku) until the probe result and a
measured saving are recorded. Shizuku stays opt-in, and MediaProjection stays the
default and fallback, as the maintainer decided on 2026-10-12.

### 4. Ideas not yet scheduled

[roadmap-2.5.0.md](roadmap-2.5.0.md) lists ideas that are not scheduled: general scene
transitions, Palette Studio, preset playlists, quick scene actions, scene packs,
different effects per stick, audio and ambient extensions, adaptive performance and
notification lighting. Each needs a design note and a measured gate before any code.

## Maintenance

- **CI:** the `Android checks` workflow runs the unit tests and the debug build on every
  push and pull request. The release build and critical lint are not in CI. The
  maintainer runs them locally (rule 10).
- **Device-only behaviour:** LED output on each stick, notifications, Android 13 and 14+
  capture consent, and OEM lifecycle limits. Unit tests cannot prove these. Each PR says
  which checks ran and on which device.
- **Regression rules:** each regression gets a rule in `AGENT_INSTRUCTIONS.md` and a test
  where possible.
- **Documentation:** `CHANGELOG.md` for every version, `technical.md` for implementation
  detail, and `docs/` for release notes and designs.
- **Licence and credits:** keep `LICENSE`, `CREDITS.md` and the BiFrost attribution.
  Keep the Git history intact and do not rewrite it.
- **Upstream:** fork-specific identifiers (`io.github.tufein.duofrost`) stay out of
  upstream submissions (rule 6c).

## Release process

1. Set `versionName` and `versionCode` in source before signing. The version code must
   exceed every earlier production-signed build (rules 30 and 39).
2. Merge through a pull request with CI green.
3. Build, sign and test on a device (see step 1 above).
4. Create a draft release, upload the signed APK and checksums, and verify the digests.
5. Publish only stable releases. Pre-releases and debug builds stay drafts unless the
   maintainer decides otherwise.
6. Keep earlier release archives. A published APK is never replaced; a fix gets a new
   version.

## Not verified yet

- LED output on the Thor for every stick mode, and the Battery Saver ceiling.
- Notification visibility under Android channel settings.
- Audio capture permission flows on Android 13 and 14+.
- Automatic backup folders on real storage providers.
- The privilege route for screen readout (`PServerBinder` user, Shizuku).

## Not promised

No release dates. The scope changes only when the maintainer decides.
