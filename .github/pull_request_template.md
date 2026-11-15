Describe the problem and the resulting behaviour, with the AYN model and firmware you tested on.

Validation: `bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`, the relevant
regression tests, and the device checks you actually performed. LED, scene and external-API paths
cannot be proven off-device: name what stayed unverified instead of implying a pass.

No new permission, no change to the plugin catalog source or the `commit()` durability pattern
without an explicit reason. Update `CHANGELOG.md`, keep the GPLv3 licence and the BiFrost
attribution intact, and see [SECURITY.md](../SECURITY.md) for reporting anything sensitive.
