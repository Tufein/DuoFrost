# Security and maintenance

DuoFrost controls device hardware and can expose that control to other apps. Keep device-specific
identifiers, private plugin sources and any account material out of public issues and CI artifacts.

Use GitHub private vulnerability reporting **if the repository owner has enabled it**. No separate
confidential contact is configured in this repository. If private reporting is unavailable, open a
minimal issue requesting a private contact, without publishing exploit details.

## What this app trusts

- **`CONTROL_LEDS` and the external API** are `normal`-protection: every installed app can request
  them, and one user toggle governs all callers. There is no per-package allowlist yet, so a
  malicious app that asks the user for the toggle can drive the LEDs; rate limits only bound the load.
- **The plugin catalog** is fetched over HTTPS from this repository and is not signed. A checksum is
  verified only when the catalog provides one. Treat catalog content as trusted input from the
  maintainer, not from the network.
- **`RECORD_AUDIO`** is used only for audio-reactive lighting; samples stay on the device and are
  never uploaded.

## Maintenance procedure

- Build and test with `bash gradlew :app:testDebugUnitTest :app:assembleDebug` before release;
  device-only paths (LED service, Smart Scenes) need a physical AYN check.
- Review Gradle and GitHub Actions updates weekly via Dependabot; record the device model and
  firmware each release was validated on in the changelog.
- Publish checksums with the release APK and keep the signing identity stable across updates.

Security claims here describe the current design, not an audit. No response SLA is promised.
