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
