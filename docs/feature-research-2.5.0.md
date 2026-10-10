# DuoFrost 2.5.0 — feature research

Reviewed on 10 October 2026. This note records the next useful additions and
the reasons for their order. Planned features are not promises of a release
date or hardware performance.

## Build on the existing lighting features

BiFrost already provides ambient colours, audio-reactive lighting, animations,
app profiles, charging and temperature indicators, preset sharing and artwork.
These are part of DuoFrost's foundation, rather than new 2.5.0 features.
[BiFrost documentation](https://github.com/Pollux-MoonBench/Bifrost).

OpenRGB's plugins demonstrate related uses: richer effects, hardware indicators
and scheduled lighting. DuoFrost already has these categories. The useful next
step is to make the existing choices easier to combine and use on a handheld.
[OpenRGB plugins](https://openrgb.org/plugins).

## Selected for the second alpha

| Addition | Daily benefit | Stability requirement |
| --- | --- | --- |
| Favourites and named collections | Find frequently used looks without scrolling through the whole library. | Keep stable preset references and leave lighting unchanged while filtering. |
| Local effect preview | Compare saved colours and supported movement before applying. | No LED commands, capture, permissions or saved changes; explain illustrative modes. |
| Selective import review | Keep only wanted presets and choose how conflicts are handled. | Protect managed presets and existing scene links; app assignment import is opt-in. |
| One temporary undo | Recover from a mistaken deletion or preset import. | Restore only while relevant data is unchanged; retain needed artwork and bound storage. |
| Calmer scene switching | Avoid reacting to brief foreground changes. | Respect permission withdrawal and expiry; reuse existing monitoring rather than add another loop. |

The preview respects Android's animation setting. Android exposes animation
availability through `ValueAnimator.areAnimatorsEnabled()`; disabled animations
can follow a zero animation scale or system power behaviour.
[ValueAnimator reference](https://developer.android.google.cn/reference/android/animation/ValueAnimator).

## Next candidates

| Candidate | Proposed experience | Evidence needed before release |
| --- | --- | --- |
| Palette Studio | Save colour palettes and extract editable colours locally from a chosen picture. | Image handling, consistent left/right colours and controller-friendly editing. |
| Palette Flow | Move through a chosen sequence of colours at a controlled speed. | Predictable output, brightness limits and measured cost on AYN hardware. |
| General scene transitions | Choose a smooth or immediate change between automatic scenes and presets. | One owner for output; Stop and mute remain immediate during transitions. |
| Preset playlists | Cycle through chosen looks with duration, repeat and optional shuffle. | Resolve priority with scenes and temporary choices before adding another scheduler. |
| Optional pinned shortcuts | Open a chosen favourite directly from a supported launcher. | Stable preset references, deleted-preset handling and the launcher's confirmation flow. |

These are proposals derived from the existing code and the research, not
functions already present in alpha.2. Existing widgets and the Quick Settings
tile remain available. Android supports static, dynamic and user-pinned shortcuts;
launcher support and limits vary.
[Android shortcuts](https://developer.android.com/develop/ui/compose/system/shortcuts).

## Stability before expanding capture

New ambient or audio features must keep Android's capture lifecycle intact. On
Android 14 and later, a new MediaProjection session requires user consent and
a capture token cannot be reused for another session. Future features cannot
promise silent capture recovery after process loss.
[Media projection](https://developer.android.com/media/grow/media-projection).

Before the final 2.5.0 release, test on AYN hardware: actual colours, permission
withdrawal, app switching, screen changes, sleep/wake, CPU load and battery use.
Emulator checks can validate interface and data flows, but cannot establish LED
compatibility or a battery improvement. The maintainer's device is AYN Thor;
other AYN models need their own confirmed results.

See the [roadmap](roadmap-2.5.0.md) for delivery stages and
[technical.md](../technical.md) for implementation and verification details.
