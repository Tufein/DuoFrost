# DuoFrost 1.2.0 verification

Verified on 2026-10-03. Based on BiFrost 1.3.1 (`1baddf1`), with original Git history retained.

- JVM tests: **123 passed, 0 failures, 0 errors, 0 skipped**.
- Includes 25 new regression tests for recovery decisions and configuration:
  explicit Stop, background opt-out, app opening, boot/update scheduling priority, unsaved
  settings, independent stick colors, strict validation and exclusion of capture
  tokens and external leases. All previous 98 tests still pass.
- Signed, optimized release APK: **build successful**.
- Release-critical Android lint: **passed**.
- APK signature: verified, RSA 3072, APK Signature Scheme v2.
- Application ID: `io.github.tufein.duofrost`.
- Version: `1.2.0`; version code: `19`; minimum Android SDK: `33`.
- Signing certificate SHA-256: `a0402863156665d4c6401bbb4a632c574978aca7000281196fc3cfa2cfc3b201`.
- Same certificate as 1.0.0 and 1.1.0, allowing an in-place update.
- Release APK and source ZIP hashes are in the release's `SHA256SUMS.txt`.
- XML layouts parsed without duplicate IDs; LEDService explicitly declares
  `stopWithTask="false"` and its foreground-service permissions/types.
- `git diff --check`: passed.

Command: `bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease`
Toolchain: JDK 17, Gradle 8.13, Android SDK 36.

These JVM tests validate recovery data and decisions; they do not execute
Android's service lifecycle or AYN firmware. Clear all, process recovery,
battery settings, capture consent and physical LED output still require
verification on an AYN Thor. See IMPROVEMENTS.md for that checklist. Android
Force stop prevents automatic restart; firmware-specific background kills
cannot be guaranteed against. Screen/audio capture needs fresh consent after
process death.

GitHub Actions runs debug builds and unit tests. Signing keys and passwords
stay private, excluded from Git and release assets. Existing API deprecation
warnings do not prevent the build.
