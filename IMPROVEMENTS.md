# DuoFrost improvements

This is a modified GPLv3 version of Pollux-MoonBench/Bifrost, based on
upstream commit `1baddf1` (1.3.1). Original authorship and license are retained.

## New in 1.2.0

- Background use is enabled by default and independent of boot auto-start or
  the external API. Recents removal no longer intentionally stops lighting.
- Running service commands preserve Android's sticky restart policy, including
  parameter updates and third-party commands. Null restart intents restore a
  validated snapshot of the base user configuration, subject to current schedules.
- Runtime recovery state is kept outside exported preferences. Capture consent
  tokens, external leases, bursts and transient app-profile decisions are excluded.
- Explicit Stop from the app, notification, tile or schedule persists the off
  state before shutdown. Activity state synchronization does not issue Stop.
- Opening the app preserves an already running capture session and respects
  a previous Stop when auto-start is disabled.
- Configuration changes apply in place. There is no stopped-service interval
  whose restart can be cancelled by closing the activity.
- Capture consent is consumed once. Missing, revoked or expired capture sessions
  keep the LED service active, turn off capture output and request fresh consent.
  The foreground service uses specialUse while awaiting capture permission,
  including startup from boot. Settings includes a capture resume button.
- Startup restores custom battery/CPU palettes, adaptive brightness and the Thor
  display selection. With no saved preset, it can use the last configuration.
- Boot schedules take priority over auto-start; package updates respect Stop.
  Schedule alarms are rearmed before service actions, so rejected starts do not
  prevent future transitions.
- Battery exemption and app-settings shortcuts, readable background status and
  an optional report containing at most three own-app process exit records.
- No new polling, restart alarms or permanent wake locks are introduced.

## Added in 1.1.0

- Optional Battery Saver dimming limits actual RGB output to 25%, including
  bright effect bursts and third-party LED frames. It responds to Android's
  Battery Saver broadcasts and adds no polling or wake lock.
- Output limiting preserves hue and separate left/right colors. Changing the
  limit redraws the current frame without restarting capture or changing presets.
- Schedule rules can select weekdays and weekends. Overnight rules belong to
  their starting day; all-day rules recheck at local midnight, including DST.
- Schedule alarms retain the actual time-zone offset. Repeated wintertime hours
  and skipped summertime hours select a strictly future alarm and recheck at the
  clock transition, preventing immediate repeated alarms or delayed starts.
- Older schedule data without weekdays still applies every day. Invalid weekday
  selections are rejected, and the editor requires at least one selected day.
- "LEDs off" schedule rules no longer require a saved preset.
- Settings can request Android to add the DuoFrost Quick Settings on/off tile.
- The first-run message now links to the DuoFrost repository.

## Changes in 1.0.0

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

For 1.2.0, first test the reported Clear all problem:

1. Leave **Keep running in background** on, leave **Start automatically** off,
   start Rainbow or Static, open several other apps and tap **Clear all** in
   Recents. Lighting should continue. Repeat after changing brightness or colors.
2. Turn background mode off and remove DuoFrost from Recents. Lighting should
   stop. Turn it on again to restore the new default behavior.
3. Check **Stop** in the app, notification and tile. Closing/reopening the app
   with automatic startup disabled must keep lighting off. Install an update
   after Stop and confirm that it also stays off.
4. Allow background lighting through Android's prompt. Return to Settings and
   check that the battery and notification status reflects Android's settings.
5. Start Ambient using accessibility capture, close other apps and use Clear all.
   Check that colors continue following the screen, including Thor close/open.
6. For MediaProjection or audio capture, an actual process kill requires new
   consent. Tap the permission notification or **Resume screen/audio capture**.
   Confirm the original lighting settings resume without a frozen service.
7. Enable auto-start with an active LEDs-off schedule and reboot. LEDs must stay
   off. A capture preset at startup should wait for consent; Static should start.
8. With no saved presets, start an unsaved Static configuration, stop it and
   restart from the tile. Confirm the last colors and brightness are restored.
9. Share a background report. Confirm it shows the app version and background
   settings; cancelling the chooser must send nothing.

Android may delay system restoration. ADB `am force-stop` cannot test sticky
process recovery because force-stop blocks automatic restarts. OEM-specific
process killing and these physical LED/capture paths need Thor verification.

Previous lighting checks remain useful:

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
6. Enable **Battery Saver LED dimming**, activate Android Battery Saver and
   check Static, PipBoy bursts and a third-party frame with different left/right
   colors. Output should dim without changing hue, restarting capture or merging
   stick colors; switch Battery Saver off to restore normal output.
7. Create Friday-only 20:00–07:00 and weekend all-day rules. Check the Friday to
   Saturday transition, midnight and any seasonal date restrictions.
8. Add the Quick Settings tile through Settings. Check adding, cancelling,
   requesting it a second time, and turning lighting on/off with the tile.

A debug APK uses `io.github.tufein.duofrost.debug` and can be installed alongside the
original app. Run only one LED controller at a time during device testing.
