# Security and maintenance

DuoFrost controls device hardware and can expose that control to other apps. Keep device-specific
identifiers, private plugin sources and any account material out of public issues and CI artifacts.

Use GitHub private vulnerability reporting **if the repository owner has enabled it**. No separate
confidential contact is configured in this repository. If private reporting is unavailable, open a
minimal issue requesting a private contact, without publishing exploit details.

## What this app trusts

- **`CONTROL_LEDS` is `normal`-protection**, so any installed app can request it. The external API
  therefore requires the master toggle **and** an explicit per-package approval. Bound-service calls
  prove the caller through the binder UID; the exported broadcast receiver cannot, so legacy
  broadcasts are off until the user enables them and their commands are treated as untrusted
  identity. Rate limits (8 burst / 4 per second, monotonic clock) only bound the load.
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
