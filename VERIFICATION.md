# DuoFrost 1.5.1 verification

Verified on 2026-10-04. Based on BiFrost 1.3.1 (`1baddf1`), with original Git history retained.

- JVM tests: **164 passed, 0 failures, 0 errors, 0 skipped**.
- Includes a new global-settings recovery regression: adaptive brightness and
  notification changes survive JSON restoration while effect, separate colors,
  palette and display remain intact. Background continuation and one-shot refresh
  requests stay outside the base-effect snapshot.
- Retains 28 tests introduced in 1.5.0: monotonic sleep-timer deadlines, process
  snapshot restoration and reboot/overflow handling; strict behavior-backup types,
  limits and legacy category compatibility; bounded widget preset parsing,
  declared widget configuration, private receivers and RemoteViews compatibility;
  diagnostic caps, mute and four-zone restoration, including unwritten zones.
  All previous output, recovery, capture, schedule and plugin tests still pass.
- Signed, optimized release APK: **build successful**.
- Release-critical Android lint: **passed**.
- APK signature: verified, RSA 3072, APK Signature Scheme v2.
- Application ID: `io.github.tufein.duofrost`.
- Version: `1.5.1`; version code: `22`; minimum Android SDK: `33`.
- Signing certificate SHA-256: `a0402863156665d4c6401bbb4a632c574978aca7000281196fc3cfa2cfc3b201`.
- Same certificate as previous DuoFrost releases, allowing an in-place update.
- Release APK and source ZIP hashes are in the release's `SHA256SUMS.txt`.
- XML layouts parsed without duplicate IDs; LEDService explicitly declares
  `stopWithTask="false"` and its foreground-service permissions/types.
- `git diff --check`: passed.

Command: `bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease`
Toolchain: JDK 17, Gradle 8.13, Android SDK 36.

These JVM tests validate timer/data policies, backup compatibility, output math
and Android component declarations; they do not execute Android's service
lifecycle, widget host or AYN firmware. Timers during device sleep, widgets,
LED test/mute, immediate Start followed by Clear all, private visible-state
events, dual-display launch requests, capture consent and physical LED output require
verification on an AYN Thor. See IMPROVEMENTS.md for that checklist. Android
Force stop prevents automatic restart; firmware-specific background kills
cannot be guaranteed against. Screen/audio capture needs fresh consent after
process death. Timer alarms can be deferred during device sleep; the timer only
stops lighting and never launches a foreground service.
The new retained-output Stop sends one best-effort black frame through Thor's
hardware binder when no service remains; it does not start a service or retry.

GitHub Actions runs debug builds and unit tests. Signing keys and passwords
stay private, excluded from Git and release assets. Existing API deprecation
warnings do not prevent the build.
