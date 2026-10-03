# Verification

Based on upstream `1baddf1` (BiFrost 1.3.1).

- Android debug build: successful.
- JVM unit tests: 67 passed, 0 failures, 0 errors, 0 skipped.
- Includes 7 new screen sampling regression tests, including 4,096 RGB combinations.
- `git diff --check`: passed.
- Debug APK uses `com.moonbench.bifrost.debug`; it can coexist with the original installation.
- Physical Thor LED output, capture lifecycle, and sleep/wake behavior: not tested here.

Command: `bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`
Toolchain: JDK 17, Gradle 8.13, Android SDK 36.

Existing deprecation warnings and an unstripped third-party native library warning
do not prevent the debug build. GitHub Actions is configured, but has not been run
on GitHub. See IMPROVEMENTS.md for the device test checklist.
