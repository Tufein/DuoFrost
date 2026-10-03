# DuoFrost 1.1.0 verification

Verified on 2026-10-03. Based on BiFrost 1.3.1 (`1baddf1`), with original Git history retained.

- JVM tests: **98 passed, 0 failures, 0 errors, 0 skipped**.
- Includes 31 new regression tests for weekday schedules, DST transitions,
  Battery Saver limits, RGB hue preservation and independent hardware-zone frames.
- Existing capture regression tests, including 4,096 RGB combinations, still pass.
- Signed, optimized release APK: **build successful**.
- Release-critical Android lint: **passed**.
- APK signature: verified, RSA 3072, APK Signature Scheme v2.
- Application ID: `io.github.tufein.duofrost`.
- Version: `1.1.0`; version code: `18`; minimum Android SDK: `33`.
- Signing certificate SHA-256: `a0402863156665d4c6401bbb4a632c574978aca7000281196fc3cfa2cfc3b201`.
- Same signing certificate as 1.0.0, allowing an in-place update.
- Release APK and source ZIP SHA-256 hashes are in the release's `SHA256SUMS.txt`.
- `git diff --check`: passed.

Command: `bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease`
Toolchain: JDK 17, Gradle 8.13, Android SDK 36.

The new Battery Saver output limit, Quick Settings prompt and live schedule
transitions still require verification on an AYN Thor. See IMPROVEMENTS.md for
that checklist. Existing upstream deprecation warnings and a third-party native
library warning do not prevent the release build.

GitHub Actions runs debug builds and unit tests. Release signing keys and
passwords stay private, excluded from Git and from release assets.
