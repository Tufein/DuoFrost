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
3. **Vendor or firmware hook.** Unknown whether AYN's firmware exposes anything
   for its LED service. Needs inspection on the device.
4. **Keep MediaProjection, reduce its cost.** Pause the mirror when the screen is
   off or the picture is static, adaptive frame rate (drop to Low when colours
   barely change). Works everywhere and needs no extra permission.

Option 4 is the only one that is safe without a device. Options 1 to 3 are
research: Android's restricted screen reading rules
(<https://source.android.com/docs/core/permissions/restricted-screen-reading>)
limit this, and a Shizuku dependency would be new for the project.

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

## Open questions for the maintainer

- Is the Thor rooted, or is ADB/Shizuku acceptable to ask of users?
- Which battery numbers do the existing tests give per profile?
