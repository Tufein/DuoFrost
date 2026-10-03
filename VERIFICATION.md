# DuoFrost 1.0.0 verification

Verified on 2026-10-03. Based on BiFrost 1.3.1 (`1baddf1`), with original Git history retained.

- JVM tests: **67 passed, 0 failures, 0 errors, 0 skipped**.
- Includes 7 screen-sampling regression tests; one covers 4,096 RGB combinations.
- Signed, optimized release APK: **build successful**.
- Release-critical Android lint: **passed**.
- APK signature: verified, RSA 3072, APK Signature Scheme v2.
- Application ID: `io.github.tufein.duofrost`.
- Version: `1.0.0`; version code: `17`; minimum Android SDK: `33`.
- Release APK SHA-256: `ce57bbc7748c3c6e0b4a38df6a6fb94a6d1e7913220a89908f22d6d0f047039c`.
- Signing certificate SHA-256: `a0402863156665d4c6401bbb4a632c574978aca7000281196fc3cfa2cfc3b201`.
- `git diff --check`: passed.

Command: `bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease`
Toolchain: JDK 17, Gradle 8.13, Android SDK 36.

Physical Thor LED output, capture permissions, credits navigation and sleep/wake
behavior were not tested here. See IMPROVEMENTS.md for the device checklist.
Existing upstream deprecation warnings and a third-party unstripped native
library warning do not prevent the release build.

GitHub Actions is configured for debug builds and unit tests. Release signing
keys and passwords are private, excluded from Git and from release assets.
