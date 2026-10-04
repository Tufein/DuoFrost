# DuoFrost 1.3.0 verification

Verified on 2026-10-04. Based on BiFrost 1.3.1 (`1baddf1`), with original Git history retained.

- JVM tests: **135 passed, 0 failures, 0 errors, 0 skipped**.
- Includes 12 new output-limit regression tests: upgrade defaults, opt-in and
  system Battery Saver gating, minimum-of-ceilings behavior, screen-off/wake
  restoration, safe stored types and bounds, percentage conversion, hue/dim
  color preservation and four independent raw zone colors. All previous 123
  recovery, capture, scheduling, plugin and rendering tests still pass.
- Signed, optimized release APK: **build successful**.
- Release-critical Android lint: **passed**.
- APK signature: verified, RSA 3072, APK Signature Scheme v2.
- Application ID: `io.github.tufein.duofrost`.
- Version: `1.3.0`; version code: `20`; minimum Android SDK: `33`.
- Signing certificate SHA-256: `a0402863156665d4c6401bbb4a632c574978aca7000281196fc3cfa2cfc3b201`.
- Same certificate as 1.0.0, 1.1.0 and 1.2.0, allowing an in-place update.
- Release APK and source ZIP hashes are in the release's `SHA256SUMS.txt`.
- XML layouts parsed without duplicate IDs; LEDService explicitly declares
  `stopWithTask="false"` and its foreground-service permissions/types.
- `git diff --check`: passed.

Command: `bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease`
Toolchain: JDK 17, Gradle 8.13, Android SDK 36.

These JVM tests validate output ceilings, color data and recovery decisions; they do not execute
Android's service lifecycle or AYN firmware. Clear all, process recovery,
screen-off dimming, battery settings, capture consent and physical LED output require
verification on an AYN Thor. See IMPROVEMENTS.md for that checklist. Android
Force stop prevents automatic restart; firmware-specific background kills
cannot be guaranteed against. Screen/audio capture needs fresh consent after
process death.

GitHub Actions runs debug builds and unit tests. Signing keys and passwords
stay private, excluded from Git and release assets. Existing API deprecation
warnings do not prevent the build.
