# Capture stability improvements

This is a modified GPLv3 version of Pollux-MoonBench/Bifrost, based on
upstream commit `1baddf1` (1.3.1). Original authorship and license are retained.

## Changes

- Ambient saturation boost preserves purple and pink hues. Previously Kotlin's
  negative remainder sent red-dominant purple colors into the red hue sector.
- Custom sampling retains its 32-pixel-wide grid in single-color mode, allowing
  vivid-pixel weighting to work instead of operating on one averaged pixel.
- Screenshot bitmaps are recycled only after processing, and aliases returned
  by Android's scaling API are recycled only once.
- Screenshot hardware buffers and capture-in-flight state are released even
  when bitmap conversion or color processing fails. The next capture is retried.
- Late accessibility screenshot callbacks are discarded after stop/restart,
  while still closing their hardware buffer.
- Failed MediaProjection startup cleans up capture resources before propagating
  the error, and a new capture session resets its frame timer.
- Capture math is isolated from Android APIs and covered by regression tests.
- GitHub Actions runs unit tests and builds a debug APK on pushes and pull requests.

## On-device verification still required

1. Start Ambient with saturation boost and show magenta/pink content on the top
   display. Both LED colors should retain the correct hue.
2. Enable custom sampling and single-color mode on letterboxed content; compare
   the color with custom sampling disabled.
3. Start/stop Ambient repeatedly with accessibility capture and MediaProjection.
   Confirm colors continue to update and memory does not grow across sessions.
4. Close/reopen the Thor while capturing, then revoke capture permission and
   start again. Confirm recovery without a frozen capture loop.
5. Switch between mapped app profiles while capturing. Check that old capture
   callbacks do not overwrite the current profile's colors.

A debug APK uses `io.github.tufein.duofrost.debug` and can be installed alongside the
original app. Run only one LED controller at a time during device testing.
