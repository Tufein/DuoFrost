# DuoFrost

**Make your AYN device's lighting your own.**

DuoFrost brings screen-matching colors, audio-reactive lighting and custom LED effects to AYN devices, with practical controls for everyday use. Set your favorite look, keep it running in the background, dim it when you need to, or let a sleep timer turn it off.

An independent, open-source continuation of [BiFrost](https://github.com/Pollux-MoonBench/Bifrost), maintained by **Stefan van Den Broek (Tufein)**. Tested by the maintainer on **AYN Thor**.

**[Download DuoFrost](https://github.com/Tufein/DuoFrost/releases/latest)** · [What's changed](CHANGELOG.md) · [Report a problem](https://github.com/Tufein/DuoFrost/issues)

![DuoFrost preset selection](assets/screenshots/home.png)

<details>
<summary>More screenshots</summary>

![Background controls](assets/screenshots/behaviour.png)

<img src="assets/screenshots/ambient-settings.png" alt="Ambient color and sampling settings" width="460">

</details>

*App interface captured in an Android emulator; sample presets shown.*

## Your lighting, your way

DuoFrost keeps BiFrost's ambient screen colors, audio-reactive effects, Ambi Aurora, separate left/right controls, presets and app profiles. It also retains theme and backup sharing, charging/battery indicators and temperature effects.

### What DuoFrost adds

- **Better background control:** keep lighting running after closing the app or using Clear all, with recovery controls when Android interrupts it.
- **Brightness limits:** choose a maximum output, a Battery Saver limit and optional dimming when the screen is off.
- **Sleep timer and temporary mute:** stop lighting after 5, 15, 30, 60 or 120 minutes, or silence the LEDs while keeping the current effect ready to resume.
- **Home-screen widgets:** Start/Stop, mute/unmute and a favorite preset without navigating through settings.
- **More flexible schedules:** choose weekdays or weekends, including schedules that continue overnight.
- **Small everyday conveniences:** an easier Quick Settings tile setup, a six-step LED test, and separate backup of lighting and background settings.
- **Ambient fixes:** more faithful purple/pink colors, improved custom single-color sampling and more reliable capture cleanup.

## Get started

1. Download the APK from the [latest release](https://github.com/Tufein/DuoFrost/releases/latest).
2. Open it on your AYN device and follow the first-run guide.
3. Pick an effect, adjust your colors and brightness, and enable only the permissions you need.

For updates through Obtainium, add [this repository](https://github.com/Tufein/DuoFrost).

Coming from BiFrost? DuoFrost installs alongside it. Export your presets, themes or backup from BiFrost, then import them into DuoFrost. Run one LED controller at a time.

Ambient and audio effects need capture permission. These effects process samples locally and do not record or upload captured content. For background lighting, allow DuoFrost to run in the background in Android's battery settings. After an Android Force stop, open the app again.

## Releases

- **Version 2:** all DuoFrost improvements in one release, including widgets, timers, mute, brightness limits, scheduling and background behavior.
- **Version 1:** the first DuoFrost release, focused on ambient color accuracy and capture stability.

See the [two-release changelog](CHANGELOG.md) for details and [technical.md](technical.md) for build instructions, implementation notes and Android behavior.

## Open source and credits

DuoFrost is based on BiFrost by **Pollux / Pollux-MoonBench**, with contributions from **KuriGohan-Kamehameha**, **paradox**, **hupo224** and the upstream community. Original authorship and Git history are preserved; see [credits](CREDITS.md).

Source and releases are available under [GPLv3](LICENSE). Forks and contributions are welcome. DuoFrost is not affiliated with AYN.
