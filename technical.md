# DuoFrost technical notes

This document collects build, lifecycle, capture, API and maintainer information.
For installation and features, see [README](README.md). The public release history
is limited to [Version 1 and Version 2](CHANGELOG.md).

## Upstream and public numbering

DuoFrost is based on BiFrost 1.3.1, commit `1baddf1`, with the original Git history,
authorship and GPLv3 license retained. Version 1 corresponds to the original
DuoFrost 1.0.0 build. Version 2 now ships Android version `2.1.0`, version code
`23`, under tag `v2.1.0`. It combines the earlier Version 2 controls with the
audio, preset, export and custom-timer improvements below.

The original `v2` publication contained the signed `1.5.1` APK (code `22`), built
from commit `dc8fc3db61d9e8ad3e1ee5fa4c9fe5ec7c239bca`. On 2026-10-05 the maintainer
explicitly approved a new private release key and replacing that publication
with 2.1.0. Its old release metadata and assets are archived locally, and the
original `v2` tag and Git history remain available. The earlier source/test
`v2.1.0` tag object is archived before the tag is finalized for the public release.
Intermediate development releases are archived locally by the maintainer and
removed from the public release list. Their implementation remains in Git history.

## Android identity and upgrades

- Application ID and API namespace: `io.github.tufein.duofrost`.
- Minimum Android version: 13 (SDK 33); compile/target SDK 36.
- Separate installation from BiFrost; only one hardware LED controller should run.
- Legacy archive schema identifiers are retained for import compatibility.
- Debug builds use `io.github.tufein.duofrost.debug`.
- Earlier DuoFrost APKs through `1.5.1` use this signing certificate SHA-256:
  `a0402863156665d4c6401bbb4a632c574978aca7000281196fc3cfa2cfc3b201`.
- Production version `2.1.0` uses this new signing certificate SHA-256:
  `0863ff52a003ec13c5997f8f23fe24fd69f4118c0644506641edd198d87aaa2d`.
- Current production version: `2.1.0`, version code `23`, tag `v2.1.0`.
  Future updates must increment the version code and retain the new release key.
  Debug builds still display `2.1.0-debug` and are separate installations.

### One-time signing migration

Android cannot install the new certificate over an earlier production APK with
the same application ID. Before uninstalling the old app, export a full backup
with Themes, Profiles, Images and Settings selected, and save it to a document
location outside DuoFrost. Then uninstall, install `DuoFrost-2.1.0.apk`, restore
the backup and grant the permissions used by the selected effects. Reconfigure
launcher widgets if needed. Exported runtime state, capture grants and active
timers are intentionally not transferred. Later updates signed with this same
new key can install in place.

## Build and automated verification

Use JDK 17, Gradle 8.13 and Android SDK 36:

```sh
bash gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

The 2026-10-04 build record reports **164 JVM tests passed**, no failures,
errors or skipped tests, optimized signed release build and release-critical
Android lint passed. Signature verification used RSA 3072 and APK Signature
Scheme v2. GitHub Actions builds a debug APK and runs tests on pushes and PRs.
Keys and passwords are never committed or placed in CI artifacts.
The original private signing key is unavailable. The maintainer explicitly
approved its replacement for 2.1.0, including the one-time reinstall above.
The new RSA 3072 key is stored outside the repository, with a private local
backup. `keystore.properties` and all keystore files are excluded from Git.
The production APK is optimized, non-debuggable and verified with APK Signature
Scheme v2. The release download includes SHA-256 checksums and matching source.

## DuoFrost 2.1.0 (2026-10-05)

Version `2.1.0` (code `23`, tag `v2.1.0`) replaces the previous Version 2 APK.
The public changelog remains Version 1 and Version 2; this is the updated
Version 2 download, rather than a third release entry.

- Custom sleep timer entry accepts any whole number from 1 to 120 minutes, with
  the existing quick choices retained. Invalid input and cancellation preserve
  an active timer; elapsed-time expiry and reboot behavior remain unchanged.
- Audio capture ends on failed or empty reads, clears failed intensity, releases
  the recorder even if stopping fails, and unblocks reads before joining the
  worker. Session-owned buffers and identity checks isolate delayed callbacks.
- Audio Reactive and AmbiAurora send both stick colors through one existing
  dual-color command per frame, preserving distinct colors and output ceilings.
- Community preset imports validate schema and supported versions before image
  writes. Expanded archive budgets are 512 entries, 2 MiB for the manifest,
  8 MiB per other entry and 64 MiB total. Duplicate ZIP entries are rejected.
  Archive type detection precedes the full-backup reader.
  Shared preset artwork is imported once and retained while another preset
  references it, including when a preset is deleted or its image replaced.
- Custom battery and CPU palette changes now participate in the unsaved-change
  prompt. Backup, theme and preset exports report a failure when the selected
  destination cannot be opened, instead of claiming completion.

Debug builds install as the separate `.debug` application. Production builds
use the new private release key and the original production application ID.
Local verification on 2026-10-05 passed all **197 JVM tests across 27 suites**
with no failures, errors or skips, plus the debug APK, optimized signed release
build and `lintVitalRelease`. Physical playback capture and LED output for these
new source changes still require device validation.
An isolated Android emulator confirmed the certificate mismatch on an in-place
update, successful installation after uninstall, production APK launch and
restoration of a real full backup exported by the old Version 2 app.

## Device validation

On 2026-10-05 the maintainer reported completing the on-device testing before
public publication. This is the maintainer's report, separate from the recorded
JVM and build checks. UI screenshots are captured from an Android emulator;
they show the real app interface, not physical LED output on a Thor.

For future releases, repeat the relevant scenarios:

- Start followed immediately by Home/Clear all; background option on and off.
- Explicit Stop in app, widget, tile and notification; Stop after process loss.
- Timer expiry/replacement/cancellation, process restoration, sleep and reboot.
- Mute during Ambient and dual-color effects; stale mute after Stop.
- Multiple widgets, favorites, preset rename/delete/import and capture cancellation.
- Six-step LED test, timeout/close and restoration of all zones.
- Thor top/bottom-screen launches preserving widget/tile/capture-resume requests.
- RGB ceilings, Battery Saver, screen sleep/wake and dim-source hue preservation.
- Backup types, old archives and selective Settings restoration during active output.
- Ambient purple/pink sampling, repeated capture sessions and revoked consent.
- Schedule weekdays, overnight rules, all-day rules and DST transitions.
- Opening/updating after Stop, boot schedule priority and app-profile changes.

Android Force stop blocks service restoration until the app is opened again.
Firmware can kill background work; recovery is best effort. Capture after actual
process death requires new Android consent. Sleep can defer timer alarms.
An ADB force-stop is not a valid sticky-process-recovery test.

## Background service and capture

Background continuation is enabled by default and independent from boot auto-start.
Wanted-running state is durable and separate from exported preferences. Explicit
Stop is saved before shutdown; onDestroy alone is not interpreted as user intent.
Sticky recovery restores only a validated base configuration and current schedules.
Capture tokens, external leases and transient profile decisions are never persisted.

Configuration updates apply inside the active service. Adaptive brightness,
notification and ceiling changes precede profile-reset paths. Opening the app
preserves an active capture session. Initial Start is dispatched while the activity
is visible; Thor display relaunches retain the request intent and bounded retry guard.
Visible status uses private events; widgets update only on relevant events.
There is no added status polling, restart alarm or permanent wake lock.

Android 14+ MediaProjection consent is single-use. Recovery waits as a specialUse
foreground service for fresh consent; boot never replays a capture token. Failed
or revoked capture cleans up and requests consent without crashing the LED service.
Accessibility callbacks from previous sessions are discarded; hardware buffers
are closed on failures and aliased/scaled bitmaps are recycled once after processing.
Negative HSV hue is normalized and custom single-color sampling retains a grid.
Screen/audio samples for lighting are processed locally, not recorded or uploaded.

Stop and timer expiry issue one best-effort black frame if the service is already
absent and wanted state is off. This clears retained hardware output without starting
a service, retrying indefinitely or creating restart work.

## Output limits, mute and diagnostic frames

Thor ignores the brightness wire field, so output caps scale actual RGB values.
The minimum of overall, Battery Saver and screen-off ceilings applies. Raw colors
remain separate by zone, preserve hue and allow redraws without capture restart.
A dim source is never brightened. Defaults are overall 100%, Battery Saver 25%
and optional screen-off dimming disabled. Android interactive state follows sleep/wake
broadcasts; closing the lid does not guarantee sleep on every firmware.

Mute suppresses RGB output while preserving effects/capture; explicit Stop clears
mute. Diagnostic frames are overlays, capped at 25% and respecting mute/ceilings.
Current raw frames remain underneath; exit, timeout or shutdown restores them and
blacks zones the underlying effect did not write.

## Timer, schedules, widgets and backups

Sleep timers use elapsed-time deadlines, survive process restoration and end at
reboot. Expired timers block recovery. Their one-shot alarm only stops lighting,
never starts a foreground service; explicit Stop cancels timer state and alarm.
Choices are 5/15/30/60/120 minutes; notification cancellation leaves lighting running.

Schedules use ISO weekdays Monday=1 through Sunday=7. Overnight rules belong to
the starting day; legacy rules without weekdays apply daily. Local calendar
midnights and absolute instants account for repeated/skipped DST times. Empty
weekday selections are rejected and every next alarm is strictly in the future.
Boot schedules take precedence over auto-start; alarms are rearmed before service actions.

Widgets have distinct immutable PendingIntents per action and widget, private
receivers and a declared configuration activity. Capture starts through the visible
app with fresh consent. Mute never starts stopped lighting. Favorites refresh after
imports, rename/deletion and reopening the app.

Settings backup uses a typed whitelist independent of presets, themes and images.
Older archives without Settings preserve current preferences. Grants, running
state, mute, timers and widget host IDs are excluded. Ceilings, background continuation,
adaptive brightness and persistent notification apply live after restore; other
service options apply at the next normal start.

## Plugin catalog

The [repository-owned catalog](plugins/catalog.json) starts empty until integrations
are verified against DuoFrost's own package/API. BiFrost-only integrations do not
automatically control DuoFrost. The preset archive schema remains compatible.

## Release signing and publication


Repository: https://github.com/Tufein/DuoFrost
Android identity: `io.github.tufein.duofrost`

1. Increment `versionCode` for each published APK and update `versionName`.
2. Use JDK 17, run JVM tests and build the release variant.
3. Configure a **gitignored** `keystore.properties` file:

   ```properties
   storeFile=/absolute/private/path/duofrost-release.jks
   storePassword=YOUR_PRIVATE_PASSWORD
   keyAlias=duofrost
   keyPassword=YOUR_PRIVATE_PASSWORD
   ```

4. Sign every future release with the same DuoFrost key. Keep an independent,
   private backup of the keystore and its passwords. A different key cannot
   update an installed release in place. Do not commit these files.
5. Verify the APK with `apksigner verify --verbose --print-certs`.
6. Tag the exact tested source and publish the APK with a SHA-256 checksum and
   release notes. GitHub's source archive for that tag is the corresponding
   GPLv3 source; do not publish APK-only releases.

CI intentionally uses debug signing and does not receive release keys.

## Integration API


DuoFrost exposes a broadcast-based IPC API that lets other apps on the device drive its LEDs — flashing police lights during a car chase, matching the LED colour to a character's health bar, setting a preset for your launcher wallpaper, reacting to in-game events in real time. It is entirely opt-in: the user must enable **"Allow third-party LED control"** in DuoFrost settings before any command is accepted.

> **Available since public Version 1**
> **API version:** 1  
> **Min Android SDK for callers:** 33 (same as DuoFrost itself)

---

## Contents

1. [Quick orientation](#1--quick-orientation)
2. [Declare the permission](#2--declare-the-permission)
3. [Copy the constants](#3--copy-the-constants)
4. [Send a command](#4--send-a-command)
5. [Check if DuoFrost is available](#5--check-if-duofrost-is-available)
6. [Available effects](#6--available-effects)
7. [ACTION_DISPLAY — live override](#7--action_display--live-override)
8. [ACTION_CLEAR](#8--action_clear)
9. [ACTION_INSTALL_PROFILE / ACTION_UNINSTALL_PROFILE / ACTION_QUERY_PLUGIN](#9--action_install_profile--action_uninstall_profile--action_query_plugin)
10. [Result codes](#10--result-codes)
11. [Complete wrapper class](#11--complete-wrapper-class)
12. [Java example](#12--java-example)
13. [Recipes](#13--recipes)
14. [Common mistakes](#14--common-mistakes)
15. [API versioning](#15--api-versioning)

---

## 1 — Quick orientation

| Concept | What it means for you |
|---|---|
| **Commands are broadcasts** | Send an explicit ordered broadcast; read the result code if you need acknowledgement. |
| **Your override is a removable layer** | DuoFrost snapshots its current state when your override starts and restores it automatically when it ends. App-profile switching is paused while an override is active. |
| **DuoFrost must already be running** | You cannot start the foreground service remotely (Android 12+ restriction). If it's not running, commands are silently dropped. Design gracefully for this. |
| **Rate limit: 8 burst / 4 per second** | Per calling UID. Burst budget refills at 4 tokens/s. |
| **Priority arbitrates between apps** | If two apps both try to drive the LEDs, the higher-priority command wins. Each app can always update its own active override regardless of priority. |
| **No library dependency needed** | Copy the string constants below. The API surface is intentionally minimal. |

---

## 2 — Declare the permission

Add one line to your `AndroidManifest.xml`. This is a `normal`-level permission — Android grants it automatically at install time; no runtime prompt is needed.

```xml
<uses-permission android:name="io.github.tufein.duofrost.permission.CONTROL_LEDS" />
```

The user still has to flip **"Allow third-party LED control"** inside DuoFrost settings. Your commands are silently rejected until they do.

---

## 3 — Copy the constants

There is no library or AAR to depend on. Copy this object into your project:

```kotlin
object DuoFrostApi {

    // ── Identity ──────────────────────────────────────────────────────────
    const val PERMISSION         = "io.github.tufein.duofrost.permission.CONTROL_LEDS"
    const val RECEIVER_PACKAGE   = "io.github.tufein.duofrost"
    const val RECEIVER_CLASS     = "io.github.tufein.duofrost.external.ExternalApiReceiver"

    // ── Actions ───────────────────────────────────────────────────────────
    const val ACTION_DISPLAY           = "io.github.tufein.duofrost.api.ACTION_DISPLAY"
    const val ACTION_CLEAR             = "io.github.tufein.duofrost.api.ACTION_CLEAR"
    const val ACTION_INSTALL_PROFILE   = "io.github.tufein.duofrost.api.ACTION_INSTALL_PROFILE"
    const val ACTION_UNINSTALL_PROFILE = "io.github.tufein.duofrost.api.ACTION_UNINSTALL_PROFILE"
    const val ACTION_QUERY_PLUGIN      = "io.github.tufein.duofrost.api.ACTION_QUERY_PLUGIN"

    // ── Protocol ──────────────────────────────────────────────────────────
    const val API_VERSION        = 1
    const val EXTRA_API_VERSION  = "apiVersion"  // Int — always include
    const val EXTRA_REQUEST_ID   = "requestId"   // String? ≤ 64 chars, echoed in result data

    // ── DISPLAY / INSTALL_PROFILE shared extras ───────────────────────────
    const val EXTRA_EFFECT          = "effect"          // String — see §6
    const val EXTRA_COLOR           = "color"           // Int (ARGB packed) — left LED
    const val EXTRA_COLOR_RIGHT     = "colorRight"      // Int (ARGB packed) — right LED; default = color
    const val EXTRA_INTENSITY       = "intensity"       // Int 0–255 (or 0–intensityScale)
    const val EXTRA_INTENSITY_SCALE = "intensityScale"  // Int 1–255; normalises intensity range
    const val EXTRA_SPEED           = "speed"           // Float 0.0–1.0
    const val EXTRA_SMOOTHNESS      = "smoothness"      // Float 0.0–1.0
    const val EXTRA_SENSITIVITY     = "sensitivity"     // Float 0.0–1.0

    // ── DISPLAY-only extras ───────────────────────────────────────────────
    const val EXTRA_PRIORITY     = "priority"    // Int 0–100; default 50
    const val EXTRA_DURATION_MS  = "durationMs"  // Long 1–600_000
    const val EXTRA_UNTIL        = "until"       // String "NEXT_COMMAND" | "EXPLICIT_CLEAR"
    const val EXTRA_INDEFINITE   = "indefinite"  // Boolean — alias for UNTIL_EXPLICIT_CLEAR

    const val UNTIL_NEXT_COMMAND   = "NEXT_COMMAND"
    const val UNTIL_EXPLICIT_CLEAR = "EXPLICIT_CLEAR"

    // ── INSTALL_PROFILE additional extras ────────────────────────────────
    const val EXTRA_PROFILE_NAME              = "profileName"
    const val EXTRA_PLUGIN_ID                 = "pluginId"
    const val EXTRA_PROFILE_REPLACE_IF_EXISTS = "replaceIfExists"      // Boolean
    const val EXTRA_SATURATION_BOOST          = "saturationBoost"      // Float 0.0–1.0
    const val EXTRA_USE_CUSTOM_SAMPLING       = "useCustomSampling"    // Boolean
    const val EXTRA_USE_SINGLE_COLOR          = "useSingleColor"       // Boolean
    const val EXTRA_BREATHE_WHEN_CHARGING     = "breatheWhenCharging"  // Boolean
    const val EXTRA_INDICATE_CHARGING_SPEED   = "indicateChargingSpeed" // Boolean
    const val EXTRA_FLASH_WHEN_READY          = "flashWhenReady"       // Boolean
    const val EXTRA_BATTERY_LOW_COLOR         = "batteryLowColor"      // Int (ARGB) optional
    const val EXTRA_BATTERY_MID_COLOR         = "batteryMidColor"      // Int (ARGB) optional
    const val EXTRA_BATTERY_HIGH_COLOR        = "batteryHighColor"     // Int (ARGB) optional
    const val EXTRA_CPU_COOL_COLOR            = "cpuCoolColor"         // Int (ARGB) optional
    const val EXTRA_CPU_WARM_COLOR            = "cpuWarmColor"         // Int (ARGB) optional
    const val EXTRA_CPU_HOT_COLOR             = "cpuHotColor"          // Int (ARGB) optional

    // ── Result codes (ordered broadcasts only) ───────────────────────────
    const val RESULT_ACCEPTED              =  0
    const val RESULT_REJECTED_DISABLED     = -1  // toggle is off in DuoFrost settings
    const val RESULT_REJECTED_VERSION      = -2  // apiVersion mismatch
    const val RESULT_REJECTED_VALIDATION   = -3  // bad/missing extras
    const val RESULT_REJECTED_UNAUTHORIZED = -4  // caller UID could not be resolved
    const val RESULT_REJECTED_RATE_LIMITED = -5  // > 4 commands/s
    const val RESULT_REJECTED_UNKNOWN_ACTION = -6
    const val RESULT_NOT_FOUND             =  1  // plugin query miss
}
```

---

## 4 — Send a command

All commands go to the same receiver via **explicit broadcast** (package + class required on Android 8+).

```kotlin
// Helper — fire and forget
fun sendToDuoFrost(context: Context, action: String, fill: Intent.() -> Unit = {}) {
    val intent = Intent(action).apply {
        setClassName(DuoFrostApi.RECEIVER_PACKAGE, DuoFrostApi.RECEIVER_CLASS)
        putExtra(DuoFrostApi.EXTRA_API_VERSION, DuoFrostApi.API_VERSION)
        fill()
    }
    context.sendBroadcast(intent, DuoFrostApi.PERMISSION)
}

// Helper — with result callback
fun sendToDuoFrostForResult(
    context: Context,
    action: String,
    fill: Intent.() -> Unit = {},
    onResult: (code: Int, requestId: String?) -> Unit,
) {
    val intent = Intent(action).apply {
        setClassName(DuoFrostApi.RECEIVER_PACKAGE, DuoFrostApi.RECEIVER_CLASS)
        putExtra(DuoFrostApi.EXTRA_API_VERSION, DuoFrostApi.API_VERSION)
        fill()
    }
    context.sendOrderedBroadcast(
        intent,
        DuoFrostApi.PERMISSION,
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) {
                onResult(resultCode, resultData)
            }
        },
        null, Activity.RESULT_OK, null, null
    )
}
```

Use `sendBroadcast` for fire-and-forget (the common case). Use `sendOrderedBroadcast` only when you need to know whether the command was accepted — debug tooling, first-run checks, rate-limit back-off, etc.

---

## 5 — Check if DuoFrost is available

DuoFrost won't be installed on every device. Guard your calls:

```kotlin
fun isDuoFrostInstalled(context: Context): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(DuoFrostApi.RECEIVER_PACKAGE, 0)
        true
    }.getOrDefault(false)

// In your manifest, declare a <queries> block so PackageManager
// lets you inspect DuoFrost's presence:
```

```xml
<queries>
    <package android:name="io.github.tufein.duofrost" />
</queries>
```

You do **not** need to add the `<queries>` block just to send broadcasts — Android routes explicit broadcasts regardless. The block is only needed if you call `getPackageInfo`, `queryIntentActivities`, or similar package-inspection APIs.

---

## 6 — Available effects

Pass one of these strings as `EXTRA_EFFECT`. Effects requiring screen capture (`AMBIENT`, `AUDIO_REACTIVE`, `AMBIAURORA`) are blocked in the external API — they need a MediaProjection consent flow that only DuoFrost's own UI can trigger.

| Effect name | Color | R/L independent | Speed | Smoothness | Sensitivity | Notes |
|---|---|---|---|---|---|---|
| `STATIC` | ✓ | ✓ | — | — | — | Solid colour, no animation |
| `BREATH` | ✓ | ✓ | ✓ | — | — | Slow fade in/out |
| `PULSE` | ✓ | ✓ | ✓ | — | — | Sharp pulse |
| `STROBE` | ✓ | ✓ | ✓ | — | — | Hard on/off flash |
| `SPARKLE` | ✓ | ✓ | ✓ | — | — | Random pixel flicker |
| `FADE_TRANSITION` | ✓ | ✓ | ✓ | — | — | Smooth colour cycling |
| `CHASE` | ✓ | ✓ | ✓ | — | — | Sweeping left↔right |
| `RAINBOW` | — | — | ✓ | — | — | Full-spectrum cycle |
| `RAVE` | — | — | ✓ | — | — | Fast random colour changes |
| `BATTERY_INDICATOR` | — | — | — | — | — | Shows battery level |
| `CPU_TEMPERATURE` | — | — | — | — | — | Shows CPU temp |

**Color** — whether `EXTRA_COLOR` / `EXTRA_COLOR_RIGHT` have any effect.  
**R/L independent** — whether left and right LEDs can be different colours.

---

## 7 — `ACTION_DISPLAY` — live override

Drives the LEDs immediately. DuoFrost snapshots its current state, applies your command, and reverts when the override ends.

```kotlin
// Police lights for 4 seconds
sendToDuoFrost(context, DuoFrostApi.ACTION_DISPLAY) {
    putExtra(DuoFrostApi.EXTRA_EFFECT,       "STROBE")
    putExtra(DuoFrostApi.EXTRA_COLOR,        Color.RED)
    putExtra(DuoFrostApi.EXTRA_COLOR_RIGHT,  Color.BLUE)
    putExtra(DuoFrostApi.EXTRA_SPEED,        0.85f)
    putExtra(DuoFrostApi.EXTRA_INTENSITY,    255)
    putExtra(DuoFrostApi.EXTRA_DURATION_MS,  4_000L)
    putExtra(DuoFrostApi.EXTRA_PRIORITY,     70)
    putExtra(DuoFrostApi.EXTRA_REQUEST_ID,   "chase-001")
}
```

### Terminator — how long does the override last?

Specify exactly **one**. Combining them is a validation error.

| Terminator | What happens |
|---|---|
| `EXTRA_DURATION_MS` (Long, 1–600 000) | Override ends automatically after this many milliseconds. |
| `EXTRA_UNTIL = UNTIL_NEXT_COMMAND` | Ends when your app sends the next DISPLAY or CLEAR. **Default when nothing is set.** |
| `EXTRA_UNTIL = UNTIL_EXPLICIT_CLEAR` | Persists until your app sends `ACTION_CLEAR`. |
| `EXTRA_INDEFINITE = true` | Identical to `UNTIL_EXPLICIT_CLEAR`. Provided for readability. |

When the override ends (for any reason), DuoFrost reverts to whatever it was doing before: if app-profile switching was active it re-resolves the current foreground app immediately; otherwise it restores the user's last preset.

### Priority

`EXTRA_PRIORITY` is an integer 0 (lowest) to 100 (highest), default 50.

- Your own app can **always** replace its current override with a new command, regardless of priority.
- A command from a **different** app is silently dropped if the active override has a strictly higher priority.
- When priorities are equal, last-write-wins.

Use high priority (≥80) for urgent, user-visible notifications. Use default (50) for ambient effects. Reserve low priority (<30) for purely cosmetic "nice-to-have" colouring that shouldn't interfere with anything else.

### Intensity scale

If your game or app has its own brightness range, tell DuoFrost the scale so it can normalise correctly:

```kotlin
// Your brightness is 0–100, not 0–255
putExtra(DuoFrostApi.EXTRA_INTENSITY,       75)   // value
putExtra(DuoFrostApi.EXTRA_INTENSITY_SCALE, 100)  // max of your range
```

---

## 8 — `ACTION_CLEAR`

Immediately ends your app's active override. You can only clear your own override.

```kotlin
sendToDuoFrost(context, DuoFrostApi.ACTION_CLEAR)
```

Always call this from `onPause` / `onStop` if you used `UNTIL_EXPLICIT_CLEAR` or `UNTIL_NEXT_COMMAND`, so DuoFrost doesn't stay stuck on your effect after the user leaves your app.

---

## 9 — `ACTION_INSTALL_PROFILE` / `ACTION_UNINSTALL_PROFILE` / `ACTION_QUERY_PLUGIN`

Install a named preset that appears in DuoFrost's preset carousel alongside the user's own presets. Good for shipping a branded theme with your app.

```kotlin
// Install on first launch
sendToDuoFrost(context, DuoFrostApi.ACTION_INSTALL_PROFILE) {
    putExtra(DuoFrostApi.EXTRA_PROFILE_NAME,              "Emerald Trail")
    putExtra(DuoFrostApi.EXTRA_EFFECT,                    "CHASE")
    putExtra(DuoFrostApi.EXTRA_COLOR,                     Color.GREEN)
    putExtra(DuoFrostApi.EXTRA_COLOR_RIGHT,               Color.rgb(0, 200, 80))
    putExtra(DuoFrostApi.EXTRA_INTENSITY,                 200)
    putExtra(DuoFrostApi.EXTRA_SPEED,                     0.6f)
    putExtra(DuoFrostApi.EXTRA_PROFILE_REPLACE_IF_EXISTS, true)
}

// Remove on explicit uninstall flow (optional — DuoFrost auto-cleans on package removal)
sendToDuoFrost(context, DuoFrostApi.ACTION_UNINSTALL_PROFILE) {
    putExtra(DuoFrostApi.EXTRA_PROFILE_NAME, "Emerald Trail")
}
```

**Ownership:** presets are tagged with your package name. You can only uninstall presets your package installed. DuoFrost removes all your presets automatically when Android broadcasts your app's uninstall.

**Visibility:** the preset appears the next time the user opens DuoFrost (the UI reloads from SharedPreferences on resume).

**`replaceIfExists`:** if `false` (default), a second install with the same name is a no-op. Set to `true` to update the preset on each app version upgrade.

**Plugin query:** use an ordered `ACTION_QUERY_PLUGIN` broadcast with
`EXTRA_PLUGIN_ID` to check whether a Plugin Store bundle is installed. DuoFrost
returns `RESULT_ACCEPTED` with `resultData = "<pluginId>:<version>"` when the
plugin is installed, or `RESULT_NOT_FOUND` when it is not. This query does not
require the third-party LED-control toggle because it only reports DuoFrost's own
plugin inventory.

---

## 10 — Result codes

These are only returned when you use `sendOrderedBroadcast`. Plain `sendBroadcast` gives you no feedback.

| Code | Value | Meaning |
|---|---|---|
| `RESULT_ACCEPTED` | 0 | Command accepted and dispatched (or stored, for profile installs). |
| `RESULT_REJECTED_DISABLED` | -1 | The "Allow third-party LED control" toggle is off. |
| `RESULT_REJECTED_VERSION` | -2 | `apiVersion` doesn't match `API_VERSION = 1`. |
| `RESULT_REJECTED_VALIDATION` | -3 | Bad or missing extras (unknown effect name, out-of-range duration, conflicting terminators, etc.). |
| `RESULT_REJECTED_UNAUTHORIZED` | -4 | Caller UID couldn't be resolved to a package. Should not happen under normal circumstances. |
| `RESULT_REJECTED_RATE_LIMITED` | -5 | More than ~4 commands/second from your UID. Back off and retry. |
| `RESULT_REJECTED_UNKNOWN_ACTION` | -6 | Action string not recognised. Check for typos. |
| `RESULT_NOT_FOUND` | 1 | Plugin query miss. |

`resultData` contains your `requestId` string when the result is `RESULT_ACCEPTED`, useful for correlating asynchronous acknowledgements.

---

## 11 — Complete wrapper class

Drop this into your project for a clean API surface:

```kotlin
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log

/**
 * Thin wrapper for sending commands to DuoFrost.
 * No state is held; all methods are stateless helpers.
 */
object DuoFrost {

    private const val TAG = "DuoFrost"

    // ── Availability ──────────────────────────────────────────────────────

    /** Returns true if DuoFrost is installed on this device. */
    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(DuoFrostApi.RECEIVER_PACKAGE, 0)
            true
        }.getOrDefault(false)

    // ── Commands ──────────────────────────────────────────────────────────

    /**
     * Show [effect] on the LEDs for [durationMs] milliseconds, then revert.
     * [colorLeft] and [colorRight] are ARGB-packed integers (e.g. Color.RED).
     * [intensity] is 0–255.
     */
    fun flash(
        context: Context,
        effect: String = "STATIC",
        colorLeft: Int = Color.WHITE,
        colorRight: Int = colorLeft,
        intensity: Int = 255,
        speed: Float = 0.5f,
        durationMs: Long = 2_000L,
        priority: Int = 50,
        requestId: String? = null,
    ) = send(context, DuoFrostApi.ACTION_DISPLAY) {
        putExtra(DuoFrostApi.EXTRA_EFFECT,      effect)
        putExtra(DuoFrostApi.EXTRA_COLOR,       colorLeft)
        putExtra(DuoFrostApi.EXTRA_COLOR_RIGHT, colorRight)
        putExtra(DuoFrostApi.EXTRA_INTENSITY,   intensity.coerceIn(0, 255))
        putExtra(DuoFrostApi.EXTRA_SPEED,       speed.coerceIn(0f, 1f))
        putExtra(DuoFrostApi.EXTRA_DURATION_MS, durationMs.coerceIn(1L, MAX_DURATION_MS))
        putExtra(DuoFrostApi.EXTRA_PRIORITY,    priority.coerceIn(0, 100))
        requestId?.let { putExtra(DuoFrostApi.EXTRA_REQUEST_ID, it.take(64)) }
    }

    /**
     * Begin a persistent LED override. Call [clear] to end it.
     * Prefer [flash] with a duration where possible.
     */
    fun show(
        context: Context,
        effect: String = "STATIC",
        colorLeft: Int = Color.WHITE,
        colorRight: Int = colorLeft,
        intensity: Int = 255,
        speed: Float = 0.5f,
        priority: Int = 50,
        requestId: String? = null,
    ) = send(context, DuoFrostApi.ACTION_DISPLAY) {
        putExtra(DuoFrostApi.EXTRA_EFFECT,      effect)
        putExtra(DuoFrostApi.EXTRA_COLOR,       colorLeft)
        putExtra(DuoFrostApi.EXTRA_COLOR_RIGHT, colorRight)
        putExtra(DuoFrostApi.EXTRA_INTENSITY,   intensity.coerceIn(0, 255))
        putExtra(DuoFrostApi.EXTRA_SPEED,       speed.coerceIn(0f, 1f))
        putExtra(DuoFrostApi.EXTRA_INDEFINITE,  true)
        putExtra(DuoFrostApi.EXTRA_PRIORITY,    priority.coerceIn(0, 100))
        requestId?.let { putExtra(DuoFrostApi.EXTRA_REQUEST_ID, it.take(64)) }
    }

    /** End your app's current LED override. */
    fun clear(context: Context) =
        send(context, DuoFrostApi.ACTION_CLEAR)

    /**
     * Install a named preset into DuoFrost's preset list.
     * The preset is tagged to your package and removed automatically if your app is uninstalled.
     */
    fun installPreset(
        context: Context,
        name: String,
        effect: String = "STATIC",
        colorLeft: Int = Color.WHITE,
        colorRight: Int = colorLeft,
        intensity: Int = 200,
        speed: Float = 0.5f,
        replaceIfExists: Boolean = true,
    ) = send(context, DuoFrostApi.ACTION_INSTALL_PROFILE) {
        putExtra(DuoFrostApi.EXTRA_PROFILE_NAME,              name)
        putExtra(DuoFrostApi.EXTRA_EFFECT,                    effect)
        putExtra(DuoFrostApi.EXTRA_COLOR,                     colorLeft)
        putExtra(DuoFrostApi.EXTRA_COLOR_RIGHT,               colorRight)
        putExtra(DuoFrostApi.EXTRA_INTENSITY,                 intensity.coerceIn(0, 255))
        putExtra(DuoFrostApi.EXTRA_SPEED,                     speed.coerceIn(0f, 1f))
        putExtra(DuoFrostApi.EXTRA_PROFILE_REPLACE_IF_EXISTS, replaceIfExists)
    }

    /** Remove a preset your app previously installed. */
    fun uninstallPreset(context: Context, name: String) =
        send(context, DuoFrostApi.ACTION_UNINSTALL_PROFILE) {
            putExtra(DuoFrostApi.EXTRA_PROFILE_NAME, name)
        }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun send(context: Context, action: String, fill: Intent.() -> Unit = {}) {
        val intent = Intent(action).apply {
            setClassName(DuoFrostApi.RECEIVER_PACKAGE, DuoFrostApi.RECEIVER_CLASS)
            putExtra(DuoFrostApi.EXTRA_API_VERSION, DuoFrostApi.API_VERSION)
            fill()
        }
        runCatching { context.sendBroadcast(intent, DuoFrostApi.PERMISSION) }
            .onFailure { Log.w(TAG, "sendBroadcast failed", it) }
    }

    // Max duration constant exposed for callers
    private const val MAX_DURATION_MS = 600_000L
}
```

Usage:

```kotlin
// In a game: show wanted-level red for 5s
DuoFrost.flash(this, effect = "STROBE", colorLeft = Color.RED, durationMs = 5_000L)

// On a menu: ambient purple
DuoFrost.show(this, effect = "BREATH", colorLeft = Color.rgb(120, 0, 255))

// When leaving the menu
DuoFrost.clear(this)

// Ship a branded preset
DuoFrost.installPreset(this, name = "Cyber Neon", effect = "CHASE",
    colorLeft = Color.CYAN, colorRight = Color.MAGENTA)
```

---

## 12 — Java example

The API is equally usable from Java:

```java
public class MyActivity extends AppCompatActivity {

    private static final String DUOFROST_PACKAGE = "io.github.tufein.duofrost";
    private static final String DUOFROST_RECEIVER = DUOFROST_PACKAGE + ".external.ExternalApiReceiver";
    private static final String PERMISSION       = DUOFROST_PACKAGE + ".permission.CONTROL_LEDS";
    private static final String ACTION_DISPLAY   = DUOFROST_PACKAGE + ".api.ACTION_DISPLAY";
    private static final String ACTION_CLEAR     = DUOFROST_PACKAGE + ".api.ACTION_CLEAR";

    @Override
    protected void onResume() {
        super.onResume();
        sendDisplay(Color.BLUE, "BREATH", 180, 5000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Intent clear = makeDuoFrostIntent(ACTION_CLEAR);
        sendBroadcast(clear, PERMISSION);
    }

    private void sendDisplay(int color, String effect, int intensity, long durationMs) {
        Intent intent = makeDuoFrostIntent(ACTION_DISPLAY);
        intent.putExtra("effect",       effect);
        intent.putExtra("color",        color);
        intent.putExtra("intensity",    intensity);
        intent.putExtra("durationMs",   durationMs);
        sendBroadcast(intent, PERMISSION);
    }

    private Intent makeDuoFrostIntent(String action) {
        Intent i = new Intent(action);
        i.setClassName(DUOFROST_PACKAGE, DUOFROST_RECEIVER);
        i.putExtra("apiVersion", 1);
        return i;
    }
}
```

---

## 13 — Recipes

### Health bar

```kotlin
// Call this whenever the player's HP changes (debounced, not every frame)
fun onHealthChanged(context: Context, hp: Int, maxHp: Int) {
    val ratio = hp.toFloat() / maxHp
    val color = when {
        ratio > 0.6f -> Color.GREEN
        ratio > 0.3f -> Color.YELLOW
        else         -> Color.RED
    }
    DuoFrost.flash(context,
        effect    = "STATIC",
        colorLeft = color, colorRight = color,
        intensity = (ratio * 255).toInt().coerceIn(60, 255),
        durationMs = 500L,   // auto-expire after half a second so the next call wins cleanly
        priority  = 40)
}
```

### Low-battery alarm

```kotlin
fun onLowBattery(context: Context) {
    DuoFrost.flash(context,
        effect    = "STROBE",
        colorLeft = Color.RED,
        durationMs = 8_000L,
        priority  = 90)        // high — don't let other apps stomp this
}
```

### Player-colour on multiplayer connect

```kotlin
val playerColors = listOf(Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW)

fun onPlayerAssigned(context: Context, playerIndex: Int) {
    DuoFrost.show(context,
        effect    = "BREATH",
        colorLeft = playerColors[playerIndex],
        priority  = 60)
}

fun onSessionEnd(context: Context) {
    DuoFrost.clear(context)
}
```

### Ship a themed preset

```kotlin
// In Application.onCreate or first-launch flow
fun installBrandPresets(context: Context) {
    DuoFrost.installPreset(context,
        name      = "Ember Glow",
        effect    = "PULSE",
        colorLeft = Color.rgb(255, 80, 0),
        colorRight = Color.rgb(200, 40, 0),
        intensity = 220,
        speed     = 0.4f)
}
```

---

## 14 — Common mistakes

### Command is silently dropped

DuoFrost only processes commands when its foreground service is running. There is no way to start it remotely. If the user hasn't opened DuoFrost or has swiped it away, your broadcasts vanish. Guard with `isInstalled` and design your integration to degrade gracefully when absent.

### `RESULT_REJECTED_DISABLED`

The user hasn't toggled "Allow third-party LED control" in DuoFrost's settings. You may surface a friendly prompt ("Enable DuoFrost integration in DuoFrost › Settings for LED effects"), but respect their choice. Don't poll or re-prompt on every launch.

### `RESULT_REJECTED_RATE_LIMITED`

You're sending more than ~4 commands per second. Debounce game-state events before translating them to LED commands. A 200ms debounce window covers most rapid-state-change scenarios and stays well within the limit.

```kotlin
private val ledDebounce = Handler(Looper.getMainLooper())
private var ledRunnable: Runnable? = null

fun setLedColor(context: Context, color: Int) {
    ledRunnable?.let(ledDebounce::removeCallbacks)
    ledRunnable = Runnable { DuoFrost.flash(context, colorLeft = color, durationMs = 300L) }
    ledDebounce.postDelayed(ledRunnable!!, 200)
}
```

### Forgetting to call `clear`

If you use `UNTIL_EXPLICIT_CLEAR` or `UNTIL_NEXT_COMMAND` (the default), an override that starts in `onResume` must be cleared in `onPause`. If you go to background without clearing, the LEDs stay on your effect until DuoFrost is restarted.

### Not declaring `<queries>`

If you call `context.packageManager.getPackageInfo("io.github.tufein.duofrost", 0)` to check availability without the `<queries>` block, it will always return false on Android 11+. Add:

```xml
<queries>
    <package android:name="io.github.tufein.duofrost" />
</queries>
```

### Using `Color.TRANSPARENT` or `Color.BLACK` as "off"

DuoFrost won't turn the LEDs off — it sets them to the colour you specify. `Color.TRANSPARENT` is `0x00000000`, which passes alpha 0 but the hardware ignores alpha. Use `ACTION_CLEAR` to revert to DuoFrost's own state instead of trying to force colour 0.

---

## 15 — API versioning

Always include `EXTRA_API_VERSION = 1`. If a future DuoFrost release introduces a breaking change, it will increment `API_VERSION` and reject older callers with `RESULT_REJECTED_VERSION`. This makes version skew immediately visible rather than silently producing wrong behaviour.

When targeting a new API version, check the DuoFrost changelog for migration notes and update your constants. The stable contract is the string values in `DuoFrostApi` — the Kotlin object itself is just a convenient copy.
