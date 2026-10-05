# DuoFrost 2.1.0 fix update — audio stability, safer presets and custom sleep timers

Hey everyone,

I've been working on DuoFrost 2.1.0, a fix update for my open-source BiFrost fork for LED control on AYN devices.

Here's what I've improved:

- Audio effects stop cleanly after capture errors, instead of leaving a busy background loop.
- Audio Reactive and AmbiAurora update both sticks together, with fewer hardware calls and separate colors preserved.
- Preset imports reject invalid or oversized files. Shared preset images are imported once and kept while another preset still uses them.
- Custom battery and CPU colors now trigger the save-changes prompt, so those edits aren't silently lost.
- Sleep timers accept your own duration from 1 to 120 minutes. The existing quick choices are still there.
- Failed backup, theme and preset exports show a failure instead of claiming the file was saved.

Version 2.1.0 is available as source and a separate debug test build. The public DuoFrost 2 APK is unchanged.

Source: https://github.com/Tufein/DuoFrost

2.1.0 source: https://github.com/Tufein/DuoFrost/tree/v2.1.0

Debug test builds: https://github.com/Tufein/DuoFrost/actions/workflows/android.yml

Credit to Pollux / Pollux-MoonBench and the BiFrost contributors for the original app. DuoFrost is GPLv3, and forks and contributions are welcome.

If you spot another issue, let me know your AYN device and what you were doing when it happened.
