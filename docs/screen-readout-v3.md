# Low-level screen colour readout (version 3 idea)

Status: design note and feasibility plan only. No app behaviour changes yet.

## Today

Ambient and AmbiAurora read colours through `ScreenAnalyzer`
(`tools/ScreenAnalyser.kt`). The normal path mirrors the display into a tiny
`ImageReader` through MediaProjection (2×1 pixels by default, 1×1 for one colour,
about 32×18 with custom sampling), throttled by the performance profile
(Low 500 ms, Medium 100 ms, High 33 ms, Ragnarok 16 ms floor). The fallback is
`AccessibilityService.takeScreenshot()`, limited to about 10 per second.

Maintainer battery tests: 60 fps (Ragnarok) is very heavy, Low is barely
noticeable. The cost is the continuously running mirror and per-frame composition,
not the pixel reading.

## Idea

Read the small colour sample without a MediaProjection session, from a
privileged source, to cut the per-frame overhead.

## Candidate sources (none verified on the AYN Thor)

1. **Hidden display capture API** (`android.window.ScreenCapture.captureDisplay`
   and the older `SurfaceControl` screenshot). Needs the signature permission
   `READ_FRAME_BUFFER`, which a normal app cannot hold. Reachable only from the
   shell uid (ADB) or through a helper such as Shizuku, with the user's consent.
2. **`screencap` through the shell uid.** Same privilege route; produces full
   frames, so likely not lighter than today. Useful only as a reference.
3. **AYN's own hook (`PServerBinder`), the chosen direction.** The app already
   writes the LEDs through this system service (`LedController.kt`): it looks up
   `PServerBinder` via `ServiceManager`, sends `transact(0, [command, "1"])` with
   `FLAG_ONEWAY`, and the service runs the command string as a privileged user
   (it writes `/sys/class/sn3112l/...`, which normal apps cannot). So a
   privileged command channel exists on the Thor without root or Shizuku.
   Unknown: which uid it runs as, whether `screencap` or
   `ScreenCapture.captureDisplay` work from it, whether a non-oneway call can
   return output, and which AYN firmware versions have it. The one-way call
   returns nothing, so a reading would have to go through a file the app can
   read, or a reply parcel. Alternatively a helper started through it could drive
   the sysfs LEDs itself. Everything here is unverified until probed on the Thor.
4. **Keep MediaProjection, reduce its cost.** Pause the mirror when the screen is
   off or the picture is static, adaptive frame rate (drop to Low when colours
   barely change). Works everywhere and needs no extra permission.

Option 4 is the only one that is safe without a device. Options 1 to 3 are
research: Android's restricted screen reading rules
(<https://source.android.com/docs/core/permissions/restricted-screen-reading>)
limit this, and a Shizuku dependency would be new for the project.

## Probe on the Thor (read-only, over ADB)

```
adb shell id
adb shell ls -l /sys/class/sn3112l/led/
adb shell service list | grep -i pserver
adb shell 'time screencap /dev/null'
adb shell dumpsys SurfaceFlinger --latency
```

These show the shell uid, whether `PServerBinder` is registered, and how long one
full-frame capture takes (the baseline any lighter source must beat).

## Plan

1. Extract a small `ColorSource` interface from `ScreenAnalyzer` (MediaProjection,
   accessibility screenshot, future source), with the existing behaviour as the
   default and unit tests unchanged.
2. On the Thor, check what a shell-uid process can read (`dumpsys SurfaceFlinger`,
   `screencap`, `ScreenCapture.captureDisplay` through `app_process`) and measure
   cost per frame. This step needs the maintainer's device.
3. Add adaptive frame rate to the MediaProjection source (option 4).
4. Only if step 2 shows a clear saving, add a privileged source behind an
   explicit opt-in setting, with MediaProjection as the automatic fallback.

## Rules for any implementation

- Preserve invariants 9, 17 and 31 in `AGENT_INSTRUCTIONS.md` (capture
  cleanup, single-use consent, session ownership).
- A privileged source never replaces MediaProjection silently, and boot never
  starts one.
- Measure first: battery per hour per profile, before and after.

## Maintainer decisions (2026-10-12)

- Go for AYN's own hook (option 3); Shizuku stays the fallback idea.
- The Thor is not rooted.
- Shizuku is acceptable as an optional requirement. Root is not needed.
  A Shizuku source stays opt-in, with MediaProjection as the default and fallback.

## Still open

- Does the shell uid on the Thor's firmware allow `ScreenCapture.captureDisplay`
  (or `screencap`) at all, and what does one small sample cost?
- Battery numbers per profile from the existing tests, as the baseline.
