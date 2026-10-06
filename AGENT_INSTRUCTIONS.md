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
15. Background continuation is independent of boot auto-start. Every command
    leaving LEDs running must retain START_STICKY when recovery is enabled;
    null restart intents need a durable, validated base configuration.
16. Store wanted-running state separately from exported preferences. Persist
    explicit Stop before shutting down; onDestroy alone is not user intent.
    Never persist capture parcels/tokens or external leases for service recovery.
17. Android 14+ capture consent is single-use. Restore as specialUse and request
    fresh consent; boot must never start a mediaProjection session. Expired or
    revoked capture must not crash the lighting service or replay a saved token.
18. Keep configuration changes within the service. A stop/delayed-start sequence
    can be interrupted by activity onPause, leaving lighting stopped permanently.
    State-only UI updates must not trigger user Start/Stop callbacks.
19. Apply schedules before boot auto-start, preserve explicit Stop on updates,
    and rearm schedule alarms before attempting a potentially rejected service start.
20. Background diagnostics are bounded, user-triggered metadata only. Do not add
    polling, restart alarms or permanent wake locks to bypass system Stop/Force stop.
21. Combine global, Battery Saver and screen-off ceilings by taking the minimum,
    never multiplying limits or brightening dim sources. Keep raw zone colors for
    sleep/wake redraws. Observe Android interactive state via broadcasts and an
    initial snapshot; lid position is not equivalent to device sleep on every Thor.
22. Sleep timers use elapsed time, persist across process restoration and end on
    reboot. Their one-shot alarm only stops lighting; never start a foreground
    service from timer expiry. Cancel both timer state and alarm on explicit Stop.
23. Diagnostic frames are temporary overlays, never recovery configurations.
    Keep live raw colors beneath them, black unwritten zones on exit, and restore
    on activity stop, service shutdown or timeout. Output ceilings/mute still apply.
24. Widgets use distinct immutable PendingIntents per widget/action, no periodic
    updates, private control receivers and a declared configuration activity.
    Start capture through the visible app with fresh Android consent.
25. Behavior backups use a typed whitelist and an explicit Settings category.
    Missing Settings in legacy archives must preserve current preferences.
    Never transfer wanted-running state, mute, timers, grants or widget host IDs.
26. Dispatch an authorized initial foreground start while the activity is visible.
    Do not let onPause cancel the only pending start before recovery state exists.
27. Stop after process loss must also clear retained hardware output. Only issue
    one black frame when no service was stopped, no service is running and wanted
    state is off; never start a service or schedule retries just to clear LEDs.
28. Thor display relaunches must retain pending widget/tile/capture actions and
    the bounded retry guard. Do not replace the launch request with an empty intent.
29. Visible runtime status uses private events, not periodic polling. Register
    only for the visible activity and synchronize controls without user callbacks.
    Global adaptive/notification updates must also precede profile reset paths.

30. Preserve Version 1, original Version 2 and the separate 2.1.0 fix update in
    the public releases and CHANGELOG.md, as clarified by the user on 2026-10-05.
    On 2026-10-06 the user promoted 2.1.0 to the latest full release, superseding
    the previous pre-release designation. Keep
    implementation, build and API detail in technical.md; retain GPLv3 credits.
    On 2026-10-05 the user explicitly approved a new private release signing key
    for the regular production APK 2.1.0. Original Version 2 keeps its exact
    signed APK 1.5.1 under tag v2; do not replace it with the fix release.
    Older DuoFrost installations require a one-time backup and reinstall:
    instruct users to save a full backup outside the app before uninstalling,
    then install 2.1.0, import the backup and grant permissions again.
    Preserve the previous release archives locally. Keep the new key and its
    recovery backup private and use the same key for future release updates.
    Never alter a signed APK's manifest to change its internal version; build
    version metadata from source before signing. Public download links must
    identify the production APK, with debug builds labeled separately.
31. Audio capture owns a recorder, buffer and route listener per session. End on
    nonpositive reads instead of spinning; stop/release before joining a blocked
    reader and release even if stop fails. Old samples/routes/finish callbacks
    must not affect a replacement session. Use dual-color writes for both sticks.
32. Validate community preset schema/version before writing artwork. Bound actual
    expanded ZIP bytes, entry count and manifest size, including directory data;
    reject duplicate entries. Inspect the archive type before the full-backup
    reader so preset imports cannot bypass these bounds. Include every battery
    and CPU palette override when detecting unsaved lighting changes.
33. Custom sleep timer input must validate the original whole-number value in
    the existing supported range; never silently truncate pasted values. Invalid
    input and Cancel preserve the current timer. Use theme-aware input colors.
34. Browsing or filtering the preset library must never apply a lighting preset.
    Filtered cards retain original storage indices and require explicit selection.
    Read app-profile mode at click time; keep selected borders synchronized.
35. Preserve editor drafts, destination and search across activity recreation,
    without serializing capture grants or runtime state. Programmatic Spinner and
    slider synchronization must not mutate drafts or issue lighting commands.
    Skip already-selected Spinner callbacks and update slider models only for
    user input. Keep unconsumed widget/tile/capture requests after recreation.
36. Hide the home view from focus and accessibility while the editor is visible.
    Respect system bars, cutouts and IME insets; keep short-screen content scrollable.
    Use readable text, 48dp controls and contrasting swatch labels. Dashboard and
    editor output ceilings share the same preference and minimal update command.
37. Inflate activity_main in short landscape and portrait with LandscapeLayoutTest:
    control IDs stay unique and settings keep scroll room. Card setters run on
    every scroll frame; skip unchanged borders and backgrounds.
38. The interface refresh and landscape work prepared as 2.2.0 ship as the 2.3.0
    pre-release (code 24), as the user decided on 2026-10-06. 2.1.0 remains the
    latest stable release until the user promotes 2.3.0. Sign it with the 2.1.0 key.
