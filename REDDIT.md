# DuoFrost 2.1.0 released — audio fixes, safer presets and custom sleep timers

Hey everyone,

I've released DuoFrost 2.1.0, the latest full release of my open-source BiFrost fork for LED control on AYN devices. It builds on Version 2 with fixes for audio effects, preset sharing and exports, plus custom sleep timers. The signed APK is ready to download, and the original releases remain available.

Here's what I've improved:

- Audio effects stop cleanly after capture errors, instead of leaving a busy background loop.
- Audio Reactive and AmbiAurora update both sticks together, with fewer hardware calls and separate colors preserved.
- Preset imports reject invalid or oversized files. Shared preset images are imported once and kept while another preset still uses them.
- Custom battery and CPU colors now trigger the save-changes prompt, so those edits aren't silently lost.
- Sleep timers accept your own duration from 1 to 120 minutes. The existing quick choices are still there.
- Failed backup, theme and preset exports show a failure instead of claiming the file was saved.

**Updating from an older DuoFrost version? This update needs a one-time reinstall:**

1. Export a full backup from the old app and save it outside the app **before uninstalling**.
2. Uninstall the old DuoFrost, then install 2.1.0.
3. Import your backup and grant the permissions you need again.

Keep your backup until you've checked that everything restored correctly.

**Download and release notes:** https://github.com/Tufein/DuoFrost/releases/tag/v2.1.0

**Original Version 2:** https://github.com/Tufein/DuoFrost/releases/tag/v2

Source: https://github.com/Tufein/DuoFrost

The fixes have been checked with automated tests and in an Android emulator. Feedback from other AYN devices is welcome.

Credit to Pollux / Pollux-MoonBench and the BiFrost contributors for the original app. DuoFrost is GPLv3, and forks and contributions are welcome.

If you spot another issue, let me know your AYN device and what you were doing when it happened.
