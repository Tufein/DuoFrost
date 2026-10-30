# 2.6.0 roadmap preview

These six features were introduced in alpha.3; alpha.4 includes review fixes.
They still need testing
on an AYN device. Production 2.5.1 remains the latest public release.

| Feature | Where to find it | What it does |
| --- | --- | --- |
| Theme editor | Settings → Appearance → Create or edit a theme | Preview colors, validate text contrast, save a new theme or update your custom theme |
| Shared themes | All app-owned screens and dialogs | Apply the selected palette to editors, onboarding, tests, menus and dialogs |
| Dashboard layout | Settings → Appearance → Dashboard layout | Put the library or lighting card first and hide optional sections |
| Automatic backups | Settings → Device settings → Automatic backups | Choose a writable folder and enable backups after important changes |
| Preset transitions | Settings → Lighting / Device → Preset transitions | Select instant changes or smooth transitions of 250, 500, 1000 or 2000 ms |
| Scene simulator | Smart Scenes → Simulate scenes | Choose an app, local date/time, battery and charging state and inspect the winning preset |

Custom themes use six opaque RGB colors. Their text must have at least 4.5:1
contrast on backgrounds, cards and controls. You can keep up to 20 named custom
themes. Theme-only export includes the palette and colored-logo choice. Existing
Classic and OLED theme IDs still work. Android settings and permission dialogs
retain Android's own appearance.
Theme imports are limited to 128 KiB. The picker refreshes after importing a new
palette, and existing screens update when you return after changing a theme.

Dashboard controls do not apply presets or change lighting. Status, Start/Stop
and Settings remain available even when the preset library is hidden. Restore
the default layout from the same settings screen.

Automatic backups are off until you choose a folder and enable them. Changes
within three seconds are grouped and ZIP writing runs off the UI thread. No
polling, wake lock or additional lighting service is added. The screen reports
the last successful backup and failures. Earlier backups are never overwritten
or deleted; manage storage in your file manager. A system kill can interrupt a
pending backup. Folder permissions and automatic-backup activation are local;
choose the folder again after reinstalling. Keep the folder private.
Full-backup imports reject duplicate ZIP entries and incorrect preference types
before replacing settings. Expanded data is limited to 64 MiB in total, 8 MiB
per file (2 MiB for the manifest), and 4096 entries including directories.

Transitions are attached to the incoming preset and preserved by preset export
and full backup. They interpolate the previous raw zone colors towards incoming
frames. Brightness ceilings and mute still apply. Stop, capture-consent blackouts
and urgent overlays cancel interpolation. Settings changes do not restart the
running effect; the chosen transition is used on future switches.

Simulation previews scene and app-profile selection with supplied inputs. Exact
app mappings override general rules; temporary holds take precedence. Simulation
does not grant permissions, start lighting or change your configuration. A chosen
game flag represents your hypothetical input. Live schedules, alerts, external
overrides, mute and other output limits can alter real output. The optional
current hold is evaluated at current device uptime rather than a future date.

Validation covers JVM/UI lifecycle regressions, archive round trips, backup
provider failures, rule priority and bounded interpolation. Builds and Android
test compilation are checked separately. Physical LED output, Android storage
providers, notification behavior and OEM lifecycle restrictions require device
testing; compilation does not establish that those device tests ran.

Audio Reactive and AmbiAurora require Android audio permission in addition to
screen-sharing consent. Grant it in onboarding or when starting an audio effect.
Capture uses other apps' playback, not the physical microphone. Protected apps
can exclude their audio. Denying access does not block non-audio effects.
