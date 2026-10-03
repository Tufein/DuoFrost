This document contains instructions for autonomous coding agents. 

1. Use openjdk@17 when building
2. adhere to NASA coding standards
3. ensure that this application consumes minimal overhead
  a. prioritize stability and performance over feature-richness
4. ensure that lessons learned and regressions to be avoided are added to this document
5. preserve divergent material when merging from upstream
6. 
  a. do not pollute or deface upstream repo
  b. be considerate when submitting changes and ensure there are minimal conflicts
  c. ensure that material specific to this fork (like readme differences and identifiers) are not submitted upstream
7. theme settings that are user-facing, especially selected theme and coloured-logo state, must remain transferable independently of full backups so theme iteration does not require whole-app restore flows

8. DuoFrost must use `io.github.tufein.duofrost` for its Android identity and API.
   Preserve upstream credits and GPLv3. Keep release signing keys out of Git.
   Legacy lowercase backup schema identifiers are retained for import compatibility.
9. Capture regressions: normalize negative HSV hue; custom single-color sampling
   needs a grid; recycle aliased bitmaps once after use; close buffers on failure;
   discard capture callbacks from previous sessions. Run ScreenSamplingTest.
10. When adding settings controls, check for duplicate IDs: the layout has
    multiple ScrollViews. Run the release build and its critical lint before publishing.
11. Battery Saver limits must affect actual RGB output: Thor ignores the fourth
    brightness wire field. Preserve hues, separate stick colors, crossfades and
    capture sessions when limits change; keep power-state updates event-driven.
12. Schedule weekdays use ISO Monday=1 through Sunday=7. Overnight rules belong
    to their starting day, and legacy rules without weekdays mean every day.
    Use local calendar midnights across DST, and reject empty weekday selections.
13. Keep schedule alarm calculations zoned through to the absolute instant:
    repeated times have two offsets, skipped times have none, and the clock
    transition can change the active rule. Never schedule an instant in the past.
14. Global output-limit toggles must send only their own parameter and apply
    before animation reset paths, preserving external colors and suppressed profiles.
