# Reddit post draft

## Title

DuoFrost for AYN Thor: a BiFrost fork with widgets, sleep timers and better background controls

## Post

Hey everyone,

I've been working on **DuoFrost**, an independent fork of **BiFrost** for the **AYN Thor**, and I'm making the releases public after testing it on my Thor.

BiFrost already does a lot well: ambient screen colors, audio-reactive effects, Ambi Aurora, separate stick colors, presets and app profiles. DuoFrost builds on that foundation with a few things I wanted for daily use:

- Keep lighting running after closing the app or using Clear all, with controls to recover when Android interrupts it.
- Set a maximum brightness, dim the LEDs with Battery Saver, or use a separate limit when the screen is off.
- Add a sleep timer or temporarily mute the LEDs without losing the current effect.
- Use home-screen widgets for Start/Stop, mute and a favorite preset.
- Choose weekdays or weekends for schedules, and back up lighting/background settings separately.

There are also fixes for purple/pink ambient colors, custom single-color sampling and capture cleanup, plus a simple LED test.

I've kept the public release history to two versions: **Version 1** contains the initial ambient fixes, and **Version 2** brings all the later DuoFrost work together.

**[Download the APK](https://github.com/Tufein/DuoFrost/releases/latest)**  
**[Source and changelog](https://github.com/Tufein/DuoFrost)**

If you're coming from BiFrost, export your presets, themes or backup and import them into DuoFrost. They can be installed alongside each other; use one LED controller at a time. Ambient/audio effects ask for the permissions they need, and samples are processed locally.

Full credit to **Pollux / Pollux-MoonBench** and the BiFrost contributors for the original app. DuoFrost is open source under **GPLv3**, and forks and contributions are welcome.

If you try it, I'd like to hear how it works on your Thor. Please include your DuoFrost version and the effect or setting involved when [reporting an issue](https://github.com/Tufein/DuoFrost/issues).

— Stefan / Tufein

## Screenshots to attach

1. [Preset selection](assets/screenshots/home.png): the main lighting screen with sample presets.
2. [Background controls](assets/screenshots/behaviour.png): background continuation controls.
3. [Ambient settings](assets/screenshots/ambient-settings.png): screen-matching color controls.

Suggested gallery caption: **DuoFrost's app interface, captured in an Android emulator with sample presets.**
These show the actual released app. A photo of the Thor LEDs can be added separately.

## Posting note

The text above is ready to paste. This file is a draft; it has not been posted to Reddit.

