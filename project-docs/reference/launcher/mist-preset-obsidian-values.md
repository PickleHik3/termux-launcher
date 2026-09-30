# Obsidian-Music glass values (reference for the Mist preset)

Source: the jadx 1.5.6 decompile of the Obsidian-Music 2.5.1 APK
(github.com/Varun7009/Obsidian-Music, Apache-2.0; the repo itself has no source code).
Decompiled 2026-09-29, read-only. "Inferred" marks values not read directly.

The glass comes from the Haze library (dev.chrisbanes.haze 1.x; the version is inferred).
Each surface is: clip(shape), then hazeEffect (blur, background colour, tint, noise), then a 1dp border.
There is no refraction, no specular highlight and no saturation/brightness filter.

## Surface

| Value | Dark glass (`obsidianGlassEffect`) | Light glass (`lightObsidianGlassEffect`) |
|---|---|---|
| Blur | 25dp (dialogs 80dp) | 25dp as used (20dp default) |
| Background | #161822 @ 0.60 | white @ 0.15 |
| Tint | white @ 0.05 | white @ 0.05 |
| Noise | 0.08 (Haze default is 0.15) | 0.05 |
| Rim | 1dp linear gradient, white @ 0.25 to white @ 0.03, diagonal from top-left (direction inferred from the defaults) | same |
| Fallback (no blur) | #161820 @ 0.85 | white @ 0.15 |
| Radius | pills 23dp; dialogs about 28dp (inferred); FABs circle | |
| Shadow | FABs only: 16dp, spot colour primary @ 0.5 | none |

The dialog fallback is #1E2028 @ 0.92 with a 1dp white @ 0.15 border.
The dialog window blur is `setBackgroundBlurRadius(80)`.

## Motion

- Press: scale to 0.92. Pressed spring damping 0.75 / stiffness 400; release spring 0.55 / 300.
- Dialog enter: blur 20 to 0 (spring 0.82 / 400), scale 0.9 to 1.0 (spring 0.8 / 400),
  alpha 280ms FastOutSlowIn. Exit: alpha 200ms FastOutLinearIn.
- Dialog backdrop: black 0 to 0.55. Enter 280ms LinearOutSlowIn; exit 220ms FastOutLinearIn.
- Content reveal: alpha spring 0.75 / 400, blur 20 to 0 spring 0.75 / 400, scale 0.9 to 1.0 spring 0.55 / 300.
- Controls enter: blur 14dp to 0, spring 0.78 / 300.
- Nav show/hide: slide spring 1.0 / 400 plus fade 250ms (hide 200ms).
- Tokens:
  - springs: snappy 0.65 / 600, bouncy 0.5 / 400, smooth 0.75 / 1500, gentle 1.0 / 200
  - tweens: 150 / 250 / 300 / 350 / 400 / 550 ms, FastOutSlowIn
  - stagger: 30ms per item, 300ms max

Compose springs map to androidx.dynamicanimation SpringForce directly:
dampingRatio is the same number, and stiffness is in the same units.
