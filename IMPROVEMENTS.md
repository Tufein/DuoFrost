# DuoFrost improvements

This is a modified GPLv3 version of Pollux-MoonBench/Bifrost, based on
upstream commit `1baddf1` (1.3.1). Original authorship and license are retained.

## New in 1.5.1

- Home-widget mute/unmute uses a private, per-widget immutable action with an
  explicit target state. Stopped or recovering lighting is never started by mute.
- Sleep timer adds a 5-minute choice and notification cancellation. Both use the
  existing elapsed-time timer; no new alarms or polling are introduced.
- Initial Start is dispatched while the activity is visible, rather than waiting
  for a callback that onPause could cancel. Existing operation grace is retained.
- Explicit Stop and timer expiry clear retained hardware output with one bounded
  black-frame write when no service exists. The off path never starts a service.
- Visible controls receive private runtime-state events for start, stop, mute,
  timer and capture changes. State synchronization does not call user toggles.
- Thor display relaunches keep the original intent so widget favorites, tile
  start and capture-resume requests reach the selected screen.
- Widgets refresh on app initialization/resume and successful backup import,
  including removed favorites, without periodic updates.
- Adaptive brightness and persistent-notification updates apply before profile
  reset paths and send only their own parameter. Successful Settings restores
  apply those values, ceilings and background continuation before the result
  dialog, preserving active capture and external output.

## Added in 1.5.0

- Sleep timer with 15/30/60/120-minute choices and cancel. One elapsed-time
  callback and one inexact user-requested stop alarm; no restart alarm or polling.
  Timer restoration respects the original deadline, Stop clears it, and reboot
  ends the previous session timer. An expired timer blocks sticky restoration.
- Temporary RGB mute in Settings and the ongoing notification. The live effect,
  capture session, colors and ceiling settings remain intact. Mute survives process
  restoration within the running session; explicit Stop clears it.
- A home widget with Start/Stop and a per-widget saved favorite. Configuration
  can be skipped and reopened; renamed favorites follow their new names, deleted
  presets offer a new choice. Widget views update only on relevant events.
- Six diagnostic LED frames at at most 25% output. Normal frames continue to
  update beneath the overlay; closing, timeout or service shutdown restores the
  current raw frame and blacks any zones never written by the underlying effect.
- Backups add an independent typed Settings category. Transferable behavior is
  whitelisted; invalid types/ranges are rejected. Old archives without Settings
  do not reset current behavior. Runtime/capture state and widget IDs are excluded.
- Widget capture cancellation preserves existing lighting; permission requests
  retain the requested preset instead of substituting the default UI settings.
- Existing credits, signing identity, GPLv3 history and archive schema retained.

## Added in 1.3.0

- Global LED output ceiling from 0–100%, applied to actual RGB writes across
  all effects, app profiles and third-party output.
- Adjustable Android Battery Saver ceiling; 25% remains the upgrade default.
- Optional screen-off ceiling, with a default of 0% when enabled. Android sleep
  and wake broadcasts update the current frame; no new polling or wake lock.
- The lowest active ceiling wins. Raw independent zone colors are retained,
  so wake and limit changes redraw without restarting animation or capture.
- Ceiling changes use a dedicated parameter update before preset/profile reset
  paths. Settings are loaded on normal starts, scheduled starts and recovery.
- Invalid stored types fall back to defaults and integer percentages are bounded.
- Background reports include ceiling settings, screen state and Battery Saver state.

## Added in 1.2.0

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

For 1.5.1:

1. Tap Start and immediately go Home or use Clear all. The initial session should
   exist and continue with background mode enabled. Repeat after capture consent.
2. Set a 5-minute timer and keep DuoFrost visible. Expiry should turn the toggle
   off and clear the timer text. Start again, set a timer and cancel it from the
   notification; lighting should continue beyond the old deadline.
3. Mute/unmute from two widgets and the notification during a capture effect.
   Visible app/widget status should follow. After Stop, a stale mute action must
   not restart lighting; normal Start should remain available.
4. Kill only the app process, without force-stop, while lighting is on. Use widget
   Stop or allow a timer to expire before restoration. Check that retained LEDs
   go black and no service is started. This depends on Thor's hardware binder.
5. Enable launching on the bottom screen. Start a widget favorite or tile, and
   resume capture from its notification. The requested action should survive the
   display move; cancelling capture must preserve existing lighting.
6. During external output or a suppressed app profile, toggle adaptive brightness
   and persistent notification. No preset should replace the active state. Restore
   a Settings backup and check immediate ceilings/background/adaptive/notification
   values; other service behavior applies on the next normal start.
7. Restore profiles that remove a widget's favorite. The widget should offer a
   new choice immediately, including after reopening the app.

For 1.5.0:

1. Start Static and set a 15-minute timer. Close DuoFrost and other apps using
   Clear all. Lighting should stop when the timer expires; reopening with boot
   auto-start disabled must keep it stopped. Android can defer alarms in sleep.
2. Cancel a timer, replace it with a later timer, then use explicit Stop and
   start lighting again. The cancelled/old deadline must not stop the new session.
   Process recovery keeps the original deadline; reboot ends the session timer.
3. Mute/unmute from Settings and the notification during Ambient and a dual-color
   effect. Colors and capture should resume without another permission request.
   Check mute during process restoration and that explicit Stop clears mute.
4. Add two widgets with different favorite presets. Use Start/Stop, change the
   favorite, rename/delete a referenced preset and resize the widget. No periodic
   refresh is needed. App-profile mode remains in charge when enabled.
5. Select a capture preset from the widget. Grant consent and confirm the saved
   preset starts. Cancel consent while another effect is running; it must continue.
6. Open LED test during a two-color effect. Walk through all six frames, close it,
   change apps and wait out a step timeout. Check restoration of both sticks and
   all four zones, including previously unused zones. Mute/ceilings remain effective.
7. Export a Settings-only backup, change ceilings/background settings and import
   it. Presets and themes should stay intact. Import an older backup and confirm
   it does not erase current behavior settings. Other service options apply next start.
8. Check the background report's mute and timer fields. Timer expiry, widget Stop
   and normal Stop must not schedule any automatic service restart.

For 1.3.0, check the controls in **Settings → Behavior**:

1. Set Maximum LED output to 40% during Static, Rainbow and Ambient. Check both
   sticks, including different left/right colors and a dim preset. Colors must
   stay distinct; a dim source must not become brighter. Return to 100%.
2. Enable Battery Saver LED dimming, set its limit to 10%, and toggle Android
   Battery Saver. Check that 25% still appears after upgrading with no new setting.
3. Enable screen-off dimming at 0%, turn the screen off, then wake. LEDs should
   go dark and restore their current colors while the service remains wanted.
   Repeat at 15%, during Android Battery Saver and with an overall 10% ceiling.
4. Close/open the Thor lid. This option follows Android device sleep; if Thor
   keeps a display awake when closed, it should not be treated as screen-off.
   Android may separately pause or revoke a capture session during device sleep.
5. During an external LED effect, change a ceiling. The effect and its independent
   colors must remain active until the existing lease/terminator ends.
6. Set overall output to 0%, close the app, reopen it and restore 100%. The
   service should stay active. Explicit Stop must still keep it stopped.

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
