# Contributing to DuoFrost

DuoFrost is an independent, open-source continuation of BiFrost for AYN handhelds. Contributions are
welcome, especially device-specific fixes.

## Get oriented

- [AGENT_INSTRUCTIONS.md](AGENT_INSTRUCTIONS.md) records the rules this codebase is maintained by
  (LED-service races, state durability, dialog lifecycle). Read it before touching `LEDService.kt` or
  `MainActivity.kt`.
- [technical.md](technical.md) describes the lighting pipeline, plugin format and what remains
  unverified on hardware.
- [CHANGELOG.md](CHANGELOG.md) and [docs/roadmap-2.6.0.md](docs/roadmap-2.6.0.md) show what is done
  and what still needs a physical device.

## Build and test

```sh
bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

This is exactly what CI runs. Unit tests cover the pure logic (presets, scenes, plugin catalog,
dialog lifecycle); the instrumented tests in `app/src/androidTest` and any LED behaviour need a real
device, so say in your pull request which of the two you actually ran.

## Conventions that matter here

- **Device truth beats assumptions.** If a change touches hardware, state the device model and
  firmware you tested on. Untested paths stay marked as untested rather than implied.
- **Persisted state is deliberate.** `SharedPreferences.commit()` calls exist because a crash must not
  lose lighting state; switch one to `apply()` only with a reason.
- **Add a test with new logic**, or explain in the pull request why it cannot be tested off-device.
- Keep the `GPLv3` licence and the BiFrost attribution intact.

## Pull requests

Small, single-purpose branches. Describe the symptom, the cause and what you verified. A screenshot
or recording from the device helps more than prose for lighting changes.
