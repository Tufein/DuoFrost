# Guided setup (2.6.0 draft)

On the first launch, DuoFrost guides you through four steps:

1. **Permissions:** notifications for lighting controls, optional Accessibility
   for screen colors, and optional Usage access for automatic app presets.
   Enable only the access you want. You can change it later in Android settings.
2. **How it works:** choosing and editing presets, Start/Stop, capture effects,
   automation and background settings.
3. **Test your lighting:** start the temporary test and confirm red, green,
   blue, white, the left stick and the right stick. Stop other LED apps first.
   If the LEDs do not match, use the help and retry; you can enter the app with
   an unresolved test to check Device settings.
4. **Ready:** Open DuoFrost completes setup and opens the dashboard.

The test runs at up to 25% brightness and respects brightness limits and mute.
Leaving the screen, rotating the device or waiting 60 seconds without a test
action stops it. Returning requires an explicit restart. The test does not
save a preset or turn on background startup. An already running effect resumes
when the temporary overlay ends.

Existing installations keep their setup status. To try the new flow, open
**Settings → Device settings → Run onboarding again**.

**Pin lighting notification** controls whether DuoFrost requests a pinned
notification. Turning it off does not hide Android’s required notification for
running lighting. Use **Stop** to end lighting and remove its notifications.
To hide the lighting notification while lighting runs, open **Device settings →
Lighting notification visibility** and turn off Android’s **LED Service**
category. Android may still show Active apps or a screen-sharing indicator.

Appearance now also offers **OLED Blue, OLED Green, OLED Amber, OLED White**
and **Midnight Teal**. You can export the selected theme separately from presets.

This build stays in a private GitHub draft. Production 2.5.1 remains available
as the latest public release.
