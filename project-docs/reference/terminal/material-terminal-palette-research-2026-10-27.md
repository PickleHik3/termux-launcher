# Material 3 terminal palettes vs kitty and Termux: research, 2026-10-27

Scope: how established terminal palettes (kitty, upstream Termux) square with Material 3 (M3) colour roles in real implementations, for two problems in `MaterialTerminalColorScheme`: (1) red/blue text on ANSI white (slot 7) panels in dialog/whiptail, (2) grey text (slot 8) too close to the glass surfaces.

Method note. WCAG ratios below are computed from HCT tone alone. HCT tone is CIE L\*, so the relative luminance of any colour at a given tone is fixed whatever its hue or chroma; a tone-vs-tone ratio is therefore exact (to rounding), not an approximation. The throwaway script is not in the repo. APCA numbers use the constants quoted from [apca-w3.js](https://raw.githubusercontent.com/Myndex/apca-w3/master/src/apca-w3.js) lines 159-172 (normBG .56, normTXT .57, revTXT .62, revBG .65, blkThrs .022, blkClmp 1.414, scale 1.14, offset .027, loClip .1) but with a plain L\*-to-Y conversion, so treat them as +-2 Lc. Anything marked UNVERIFIED was not read from a primary source.

## 1. Summary

- **Our dark accents are far lighter than any terminal's, which is the whole slot-7 failure.** Default dark puts slots 1-6 at HCT tone 80 and slot 7 at tone 80 ([TerminalContrastLevel.java:16-18](../../../termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TerminalContrastLevel.java), [MaterialTerminalColorScheme.java:496-499](../../../app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java)), so red-on-white is 1.00:1. kitty's red and blue are tone 42.7 and 47.9 (4.31 and 3.56:1 on `#dddddd`); Termux's red is tone 42.7 (4.64:1 on `#e5e5e5`). Implication: normal accents want tone ~44-50 in the dark palette, not 80 (section 6).
- **Nobody keeps all of slots 1-6 readable on slot 7.** kitty's green `#19cb00` is 1.61:1 and yellow `#cecb00` 1.27:1 on its own `#dddddd`; Termux's blue `#6495ed` is 2.36:1 on `#e5e5e5`. What kitty actually secures is red and blue, the two inks dialog uses. Implication: floor only slots 1 and 4 (and 9/12 if bold maps to bright) on slot 7; do not try to floor all six.
- **Material-themed terminals that map roles straight across reproduce both of our problems.** matugen's kitty template sets `color0 = surface` (identical to the background), `color7 = on_surface`, `color8 = surface_variant` (tone 30 on surface tone 6 = 1.98:1), and error tone 80 on on_surface tone 90 is 1.32:1 ([matugen kitty-colors.conf](https://raw.githubusercontent.com/InioX/matugen-themes/main/templates/kitty-colors.conf)). Noctalia's generated palette maps bright black to M3 `outline` (tone 60, 5.85:1 on surface tone 6) and normal white to `on_surface`, with accents floored at 4.5:1 on the background only ([fixed_palette.cpp:181-203](https://raw.githubusercontent.com/noctalia-dev/noctalia-shell/main/src/theme/fixed_palette.cpp)). Implication: the established Material answer for slot 8 is `outline`; none of them solve slot 7 panels.
- **M3 itself puts the grey at tone 60 (dark) / 50 (light) and measures it on the lightest container, not the darkest surface.** `outline` is tone 60 dark, 50 light, with a contrast curve (1.5, 3, 4.5, 7) against `highestSurface`, which is `surfaceBright` (tone 24 dark) in dark and `surfaceDim` (tone 87) in light ([material_dynamic_colors.dart:34-36, 100-106, 198-206](https://raw.githubusercontent.com/material-foundation/material-color-utilities/main/dart/lib/dynamiccolor/material_dynamic_colors.dart)). Our Default grey is tone 45 dark: 15 tones under M3's own outline, and 3.32:1 on the tone-8 background but 2.15:1 on tone 24. Implication: slot 8 should be measured against the lightest surface it will meet (the composited glass), at M3's 3:1 or better.
- **410458fea made the grey follow Contrast, but left Default where it was and did not raise its floor.** Default is unchanged (tone 45/50, 3.0:1); Softer got dimmer (36, 2.0:1) and Harder brighter (62, 4.5:1). The floor is measured against `measuredOn`, which is the composited ground on glass, so the plumbing is right; the Default number is the problem. Implication: the developer's complaint is a Default-level problem that the commit explicitly did not touch (section 5).
- **kitty's grey is deliberately readable: `color8 #767676` is 4.62:1 on `#000`**, tone 49.6, and Termux's `#7f7f7f` is 5.24:1. Both sit well above our 3.32:1. Implication: the kitty-parity target for slot 8 is >= 4.5:1 on the ground, i.e. tone 55 or so on our tone-8 background.
- **Bright accents on slot 7 are a separate trap.** Our bright tone is 90 dark / 30 light; bold-as-bright blue (slot 12) at tone 90 on slot 7 tone 80 is 1.32:1. kitty's bright blue `#1a8fff` is 2.41:1 on `#dddddd`, so even kitty is weak there. Implication: either keep slot 12/9 darker than the background-side recipe, or accept kitty's behaviour and floor slots 9 and 12 against slot 7 only when it is cheap.
- **Blend.harmonize is what we already do**, hue rotated by min(half the gap, 15 deg) toward the source with tone and chroma held ([blend.dart:35-48](https://raw.githubusercontent.com/material-foundation/material-color-utilities/main/dart/lib/blend/blend.dart)); `harmonizeHue` is at MaterialTerminalColorScheme.java:537. No guidance in the source on terminals.

## 2. Material 3 roles and tones relevant to a terminal

All from `MaterialDynamicColors` in [material_dynamic_colors.dart](https://raw.githubusercontent.com/material-foundation/material-color-utilities/main/dart/lib/dynamiccolor/material_dynamic_colors.dart) (dart port; same tables in the Java port). Palette chroma: neutral 6, neutral-variant 8 ([scheme_tonal_spot.dart:35-36](https://raw.githubusercontent.com/material-foundation/material-color-utilities/main/dart/lib/scheme/scheme_tonal_spot.dart)); error palette is hue 25, chroma 84 ([dynamic_scheme.dart:80](https://raw.githubusercontent.com/material-foundation/material-color-utilities/main/dart/lib/dynamiccolor/dynamic_scheme.dart)).

| Role | Dark tone | Light tone | Contrast curve vs its background | Lines |
|---|---|---|---|---|
| surface / background | 6 | 98 | none | 69-74, 84-89 |
| surfaceDim | 6 | 87 (curve 87,87,80,75) | none | 91-98 |
| surfaceBright | 24 (curve 24,24,29,34) | 98 | none | 100-107 |
| surfaceContainer | 12 (12,12,16,20) | 94 (94,94,92,90) | none | 128-137 |
| surfaceContainerHighest | 22 (22,22,26,30) | 90 (90,90,84,80) | none | 150-158 |
| onSurface | 90 | 10 | (4.5, 7, 11, 21) | 161-167 |
| surfaceVariant | 30 | 90 | none | 169-174 |
| onSurfaceVariant | 80 | 30 | (3, 4.5, 7, 11) | 176-182 |
| outline | 60 | 50 | (1.5, 3, 4.5, 7) | 198-206 |
| outlineVariant | 30 | 80 | (1, 1, 3, 4.5) | 208-214 |
| error | 80 | 40 | (3, 4.5, 7, 7) | 481-487 |
| highestSurface (the background accent/outline are measured on) | surfaceBright | surfaceDim | | 34-36 |

Reading the tables:
- The curve tuple is (low, normal, medium, high) contrast level, used as `ContrastCurve(low, normal, medium, high).get(level)`. I recalled that order; the `contrast_curve.dart` fetch path returned 404, so UNVERIFIED against source (it matches the visible rows: onSurface 4.5 at normal, 21 at high, surface tones moving outward at medium/high).
- M3's own scale for secondary text: `onSurfaceVariant` is a 4.5:1 text role at standard contrast, `outline` a 3:1 non-text role (borders). A terminal's slot 8 is text, so M3's own rule would put it between the two (tone 60-80), not under outline.
- Ratios (tone arithmetic): outline 60 on surface 6 = 5.85, on container 12 = 5.17, on surfaceBright 24 = 3.64; onSurfaceVariant 80 on tone 24 = 6.78; light outline 50 on surface 98 = 4.27, on surfaceDim 87 = 3.20.
- Normal error is tone 80 dark, 40 light. Dark error on a light neutral panel is the 1.32:1 case (error 80 vs on_surface 90).
- The M3 source has no terminal-specific guidance. The nearest official concept is "custom colors": harmonize a fixed-hue colour toward the source with `Blend.harmonize`, keeping tone and chroma, then take role tones from its own tonal palette (not re-read from m3.material.io here; UNVERIFIED against the site).

## 3. How real Material-themed terminals map roles to ANSI slots

### matugen-themes: kitty ([source](https://raw.githubusercontent.com/InioX/matugen-themes/main/templates/kitty-colors.conf))

| Slot | Role | Tone (dark) | Note |
|---|---|---|---|
| bg / fg | surface / on_surface | 6 / 90 | |
| 0 | surface | 6 | same as background: black invisible |
| 1 | error | 80 | 1.32:1 on slot 7 |
| 2 | tertiary | ~80 | tone not read from source (depends on seed) |
| 3 | secondary | ~80 | |
| 4 | primary | ~80 | |
| 5, 6 | tertiary_fixed_dim, secondary_fixed_dim | | |
| 7 | on_surface | 90 | |
| 8 | surface_variant | 30 | 1.98:1 on surface 6; this is the "grey vanishes" case |
| 9-14 | *_container | ~30 | dark containers as "bright": darker than normal |
| 15 | on_surface_variant | 80 | darker than slot 7 |

Slot 7 vs 1-6: no check. Slot 8 vs bg: no check. matugen's foot template hardcodes grey-ish neutrals (`regular0=4c4c4c ... regular7=f0f0f0`, `bright0=262626`) and only injects `primary` as blue; its alacritty template hardcodes Catppuccin values (`white = '#BAC2DE'`, `bright black = '#585B70'`) and uses `error`/`primary`/`tertiary`/`secondary` for 1-4-ish ([foot-colors.ini](https://raw.githubusercontent.com/InioX/matugen-themes/main/templates/foot-colors.ini), [alacritty.toml](https://raw.githubusercontent.com/InioX/matugen-themes/main/templates/alacritty.toml)). So even in the matugen set, neutrals are borrowed from established themes rather than from M3.

### Noctalia shell ([fixed_palette.cpp:159-203](https://raw.githubusercontent.com/noctalia-dev/noctalia-shell/main/src/theme/fixed_palette.cpp), our code exports its `terminal_*` names)

| Slot | Role | Check |
|---|---|---|
| bg / fg | terminal_background (= background) / on_surface | |
| 0 | surface_variant (tone 30 dark) | the comment at [palette_transform.cpp:17-19](https://raw.githubusercontent.com/noctalia-dev/noctalia-shell/main/src/theme/palette_transform.cpp) says black "needs to stay visible against the background, not follow it" |
| 1 | error | `ensureContrast(..., 4.5)` on the terminal background |
| 2 | primary | same 4.5 floor |
| 3 | secondary | same |
| 4 | tertiary | same |
| 5, 6 | primary_fixed_dim, secondary_fixed_dim | same |
| 7 | on_surface (foreground) | none against 1-6 |
| 8 | outline (tone 60 dark) | none; 5.85:1 on tone 6 by construction |
| 9-14 | same as 1-6 | |
| 15 | foreground | |

So the one place a Material terminal exporter states a floor: accents >= 4.5:1 on the background (`ensureTerminalTextContrast`, line 144-146). Slot 7 panels are not considered; with slot 7 = on_surface (tone 90) and accent tone ~80 that is ~1.3:1, the same bug as ours. Bright black = `outline` is the closest to an official M3 mapping for a terminal grey. Noctalia's built-in fixed palettes (the Ayu-like block in [builtin_palettes.cpp:36-66](https://raw.githubusercontent.com/noctalia-dev/noctalia-shell/main/src/theme/builtin_palettes.cpp)) are hand-picked, e.g. white `#c7c7c7`, bright black `#686868` (tone 44.0; 3.47:1 on its `#0b0e14` background, computed), not derived.

### GNOME Ptyxis

Its shipped `.palette` files ([gitlab data/palettes](https://gitlab.gnome.org/chergert/ptyxis/-/tree/main/data/palettes), e.g. `sonokai.palette`) are static hand-made palettes with a `[Light]` and `[Dark]` section each (Color0..15, Foreground, Background); I found no Material-You derivation in them. Example: Sonokai Color8 `#7F8490` on Background `#2C2E34` is 3.62:1 (computed). Not evidence for M3 mapping; only for slot shape.

### Not found

I could not locate, in primary source, an Android/Termux Material-You terminal theme, a Termux:Styling successor that derives from M3, or a pywal/wallust Material mode. UNVERIFIED / gap. I did not fetch `termux-styling`'s colour files.

## 4. kitty and Termux expectations

Values from [kitty options definition.py](https://raw.githubusercontent.com/kovidgoyal/kitty/master/kitty/options/definition.py) lines 2802-2862 and [Termux TerminalColorScheme/ColorScheme](https://raw.githubusercontent.com/termux/termux-app/master/terminal-emulator/src/main/java/com/termux/terminal/TerminalColorScheme.java) (the default table is in `TerminalColors.java` lines 17-33 as fetched). Tone and ratios computed.

| Slot | kitty | tone | on `#000` | on `#ddd` | Termux | tone | on `#000` | on `#e5e5e5` |
|---|---|---|---|---|---|---|---|---|
| 1 red | `#cc0403` | 42.7 | 3.59 | 4.31 | `#cd0000` | 42.7 | 3.60 | 4.64 |
| 2 green | `#19cb00` | 71.5 | 9.58 | 1.61 | `#00cd00` | | | |
| 3 yellow | `#cecb00` | 79.5 | 12.17 | 1.27 | `#cdcd00` | | | |
| 4 blue | `#0d73cc` | 47.9 | 4.34 | 3.56 | `#6495ed` | 61.9 | 7.06 | 2.36 |
| 7 white | `#dddddd` | 88.1 | 15.46 | | `#e5e5e5` | 90.9 | | |
| 8 | `#767676` | 49.6 | 4.62 | | `#7f7f7f` | 53.2 | 5.24 | |
| 9 bright red | `#f2201f` | 51.8 | 5.00 | 3.09 | `#ff0000` | | | |
| 12 bright blue | `#1a8fff` | 59.1 | 6.42 | 2.41 | `#5c5cff` | | | |
| 15 | `#ffffff` | | | | `#ffffff` | | | |

Shape that both share:
- Normal accents are the *saturated mid tones*, brightness ~43-50 for red and blue (the slots dialog actually draws on slot 7) and up to 80 for yellow/green (which dialog never uses on white).
- Slot 7 is light, tone 88-91, not the foreground (kitty fg `#dddddd` equals color7); slot 15 is white.
- Slot 8 is mid-grey, tone 50-53, 4.6-5.2:1 on black.
- kitty does **not** floor colours: `text_fg_override_threshold` defaults to `0`, i.e. no override ([definition.py:314-345](https://raw.githubusercontent.com/kovidgoyal/kitty/master/kitty/options/definition.py)); when set it either enforces a WCAG-style ratio (HSLuv lightness move) or a % luminance difference. kitty's `dim_opacity` is 0.4 ([line 2744](https://raw.githubusercontent.com/kovidgoyal/kitty/master/kitty/options/definition.py)) for the SGR dim attribute (a different mechanism from slot 8). kitty-themes colour8-vs-background and colour1-6-vs-colour7 handling not read (UNVERIFIED; the repository wasn't fetched).

## 5. Grey text on glass: what 410458fea did and did not do

What it did (`git show 410458fea`; code at [MaterialTerminalColorScheme.java:426-430, 436-447, 493-498](../../../app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java), [TerminalContrastLevel.java:16-18, 51-62](../../../termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TerminalContrastLevel.java)):
- The recipe columns after `reach` are `dim tone dark, dim tone light, dim ratio`: Softer 36/60 at 2.0:1; Default 45/50 at 3.0:1; Harder 62/36 at 4.5:1. Before it, tone 45/50 with a fixed 3.0:1 floor at every level, so the three levels drew nearly the same grey.
- `ansiFloor(8)` now returns `level.dimRatio`; `applyAnsiContrastFloor` runs slot 8 through `contrastTone(..., DIM_TONE_MIN=10, ..., chromaKept 0)` against `measuredOn`. `measuredOn` is `OnGlass.opaque(ground)` on glass, `background` otherwise ([:230](../../../app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java)), and `ground` is documented as the composite of wallpaper, the launcher dim and the pane tint ([:157-158](../../../app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java)). So slot 8 **is** measured against the glass ground, as designed.
- `TerminalContrastGreyTest` asserts steps between levels (Harder >= 1.5x Default, Default >= 1.3x Softer) on pong's dark theme, a light theme and on glass `DUSK #1B1A17`, that slot 8 stays between slot 0 and slot 7 in tone and under the foreground's ratio, and that Default stays at tone 45/50 and 3.0 (the file ends with `defaultKeepsTheGreyItAlwaysHad`; the test file was read to line 120, tail not read).

What it did not do (numbers are tone arithmetic):
- **Default is unchanged on purpose.** Tone 45 is 3.32:1 on the tone-8 background, 3.05 on a tone-12 container, 2.68 on tone 17 and 2.15 on tone 24; on glass `DUSK` (tone 9.3) 3.24. The commit message's "about 2.0, 3.3 and 6.8:1" matches Softer 2.0 / Default 3.3 / Harder 6.8 on the background.
- The floor is a WCAG ratio on **one** ground colour. A glass pane over a bright part of the wallpaper or a lighter tint can sit at tone 17-24, where Default's grey is 2.2-2.7:1 and the lightest chrome surfaces (M3's `surfaceBright` is tone 24) read almost the same as the grey. The test's glass case is a dark wallpaper (tone 9) and therefore proves little about the real complaint.
- **Nothing keeps the grey away from the glass *tint*.** The ground is sampled once and the grey is solved against it; per-cell backgrounds (chrome bands, prompts with surface-coloured segments, selection) are not considered.
- At Default, slot 8 is darker than M3's own `outline` (tone 60 dark / 50 light) and than kitty's 4.62:1. In the light palette tone 50 is the same as M3 outline, which is why light feels fine and dark does not (light tone 50 on 97: 4.16; dark tone 45 on 8: 3.32).
- WCAG vs APCA for small text: tone 45 on tone 8 is Lc ~25.7 (reverse-polarity magnitude); tone 55 is ~37.0; tone 60 is ~43.1; tone 62 ~45.7; kitty `#767676` on black is ~33.4. APCA guidance puts body text in the Lc 60-75 range and Lc 30-45 as the floor for spot/secondary text; I could not confirm those thresholds from the spec this session (UNVERIFIED), but the relative ordering supports that Default's Lc ~26 is clearly below kitty's ~33.

What the sources suggest:
- Use M3's own ordering: grey = `outline`-like tone (60 dark / 50 light at standard contrast), text = onSurfaceVariant (80 / 30), measured on **highestSurface** (the lightest container) rather than the base surface.
- Keep the grey below the foreground; at tone 60 vs fg tone 90 the grey is 2.45:1 from the text, so it still reads as secondary.

## 6. Proposal: a recipe for our palettes (PROPOSAL, not implemented)

Everything below is arithmetic from section 2/4 tone ratios (tone-only, exact for any hue); it has not been run through the code's `contrastTone`, which also applies chroma-kept rules and could move tones.

### 6.1 Slot 8 (grey)

| | Dark | Light |
|---|---|---|
| Default tone | 56-58 (outline 60 is M3's; 55 is already 4.75:1 on tone 8) | 45 (see note) |
| Floor ground | the composited glass ground; additionally the lightest of {ground, M3 surfaceBright-equivalent tone 24} if the pane tint is unknown | same, light variant (surfaceDim tone 87) |

Dark arithmetic (ratio of tone T on ground G):

| T \ G | 8 | 12 | 17 | 24 |
|---|---|---|---|---|
| 45 (today) | 3.32 | 3.05 | 2.68 | 2.15 |
| 55 | 4.75 | 4.36 | 3.82 | 3.07 |
| 58 | 5.26 | 4.83 | 4.24 | 3.40 |
| 60 | 5.63 | 5.17 | 4.54 | 3.64 |

Suggested Default: tone 58, floor 4.5:1 on the measured ground (gives >= 4.5 on tone 8/12/17-ish; on a tone-24 ground the 4.5 floor would push it to tone ~66.5). Softer 3.0:1, Harder 6.0:1 (tone 62 already 6.25 on tone 8). Ceiling: keep the grey >= ~2.4:1 under the foreground so it is still dim (tone 60 vs 90 = 2.45; tone 58 = 2.63; tone 62 = 2.30). The light palette's floors keep tone 50 (M3 outline) on ground 97 = 4.16; the ratio stays under 4.5, so for light Default use tone 46-47 (4.81 / 4.64:1 on 97) and the same 4.5 floor, if the developer wants parity with the dark change.

### 6.2 Accents 1-6 and slot 7 (dialog panels)

Constraint solve, dark palette (bg tone 8 per Default `bgToneDark`; slot 7 tone 85-88):

- Balanced accent tone T where ratio(T, bg 8) = ratio(T, slot7):

| slot 7 tone | balanced accent tone | ratio each side |
|---|---|---|
| 80 | 44.3 | 3.24 |
| 85 | 46.2 | 3.47 |
| 88 | 47.4 | 3.62 |

- kitty is at tone 42.7 (red, 3.59 / 4.31) and 47.9 (blue, 4.34 / 3.56), so a balanced tone of ~46-48 reproduces kitty's red/blue within 0.3:1 on both sides.
- Alternative weighting that favours the background (what most text uses): accent tone 50 gives 3.98 on bg 8 and 3.29 on slot 7 at tone 88; tone 52 gives 4.27 / 3.06.
- Slot 7 itself: tone 88 (kitty `#ddd` is 88.1) rather than 80; it is a fill, exempt from the floor in `ansiFloor`, so raising it costs nothing against text rules. At tone 88 a tone-47 accent clears 3.5:1; the max tone for 3:1 is 52.6, and 4.5:1 on slot 7 would need tone <= 41.4, but 4.5 on bg 8 needs tone >= 53.5, so no tone satisfies 4.5 on both sides.

Proposal for slots 1-6, dark Default: tone ~48, floor 3.5:1 on the background ground and 3.0:1 on slot 7 for slots 1 and 4 only (the dialog inks; green/yellow/cyan fail on slot 7 in kitty and Termux as well). Bright slots 9-14: keep at tone 90 vs the bg (they are the "emphasised" text), but slots 9 and 12 get a tone cap of ~50-55 to hold 3:1 on slot 7 at tone 88 (max tone 52.6 for 3:1); that deviates from kitty's bright set (kitty 12 is 2.41:1 there), and means bold-as-bright blue is no brighter than normal blue, so I would cap only slot 12 and 9 and accept the loss of the bright/normal difference for these two. Open decision for the developer.

Light palette (bg tone 97, slot 7 tone 75 today): accent tone 40 is 6.00:1 on bg and 3.28:1 on slot 7; tone 35 is 7.22 / 3.95; tone 30 is 8.67 / 4.74. Light is already satisfied at tone <= 40; raise slot 7 to tone 80 to get 3.80:1 at tone 40, matching kitty's light-on-dark-flip habits, or leave as is. Light slot 8 at tone 50 on slot 7 (75) is 2.28:1, so it fails on panels; none of kitty/Termux claim that.

### 6.3 Floors, stated plainly

| Slot | Floor | Measured on |
|---|---|---|
| 0 | none (fill) | |
| 1-6 | level.ansiRatio (Default 4.5 today; proposal 3.5 dark, to match kitty's 3.6-4.3) | the composited ground |
| 1 and 4 (and 9, 12) | additionally >= 3.0:1 | slot 7 (the panel fill) |
| 7 | none (fill) | |
| 8 | Softer 3.0 / Default 4.5 / Harder 6.0 | the composited ground (and on a tone-24 glass worst case 3.0 holds at tone 55) |
| 15 | none (fill) | |

Why 3.5 not 4.5 on accents: today's 4.5 floor at tone 80 is met trivially (10.5:1), but the rules that block red-on-white are *not* the floor; they are the tone chosen. Lowering the chosen tone to ~48 makes the floor bind at 3.5-ish and the whole set (including saturated chroma) reads like kitty's.

## 7. Open questions and unverified

- Is the glass ground sampled at the pane's lightest or average luminance, and can per-cell chrome backgrounds exceed it? The code says "composite behind a translucent pane" ([MaterialTerminalColorScheme.java:157-158](../../../app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java)); I did not trace the sampler in `GlassBackdropCache`.
- `TerminalPanelInkTest` (uncommitted) asserts a 3.0 floor on slots 0, 1, 4, 12 on slot 7 on dark/light, opaque and glass `DUSK` at all three levels; I did not run it (no builds), so my claim that the present palette fails is from your measured 1.00:1 and from tone arithmetic, not a test run. Slot 0 on slot 7 passes by design (tone 25 on 80 = 5.7:1, tone arithmetic).
- Contrast-curve argument order (low, normal, medium, high) not confirmed from `contrast_curve.dart` (404 on the path I tried).
- Light-palette numbers for the grey and the claim "light feels fine" are inferred from ratios, not seen on a device.
- APCA Lc thresholds for small text (Lc 60/75 body, 30-45 secondary) not confirmed from the spec; my Lc values use apca-w3's constants but a plain L\*-to-Y conversion.
- kitty-themes colour8 handling, termux-styling's colour files, matugen's other templates, any Android Material-You terminal theme, and Ptyxis's own M3 logic were not read; and m3.material.io's pages (colour roles, "custom colors" guidance) were not fetched, only the source repo.
- The proposal tones are rounded; the exact tone each slot lands on after `contrastTone` (101-tone HCT search with chroma-kept rules, MaterialTerminalColorScheme.java:118-123) is not computed, so check in code before adopting.

### Verified afterwards (2026-10-27, main session)

- **Contrast-curve order confirmed** from the bytecode of `com.google.android.material.color.utilities.ContrastCurve` in material-1.14.0: the constructor stores `(low, normal, medium, high)`, and `get(contrast)` lerps low→normal over contrast −1…0, then normal→medium and medium→high.
- **The glass ground is a mean.** `GlassBackdropCache.sampleBitmapRegion` (GlassBackdropCache.java:434) averages a grid of at most `SAMPLE_GRID`² pixels of the pre-blurred wallpaper under the pane. `ChromeInk.terminalPane` (ChromeInk.java:466) composites that under the dim and the tint, and `meanPaneVote` averages across panes. That mean is the `ground` given to `MaterialTerminalColorScheme.build`. The veil is capped at the pane's opacity plus 15 points (`veilCeiling255`).
- **Pong, 1.3.0-pre.4, dark, glass over a cloud wallpaper.** A test card printed in the terminal; pixels were read from the screenshot.

  | Sample | Ink | Ground under it | Ratio |
  |---|---|---|---|
  | dialog panel, `black` on 47 | `#3c3a45` | `#c8c4d3` | 6.53 |
  | dialog panel, `red` / `blue` / bold `blue` on 47 | invisible / `#aac8fc` | `#c8c4d3` | 1.00 / 1.01 |
  | slot 8 grey over dark sky | `#b3b2b9` | `#203969` | 5.40 |
  | slot 8 grey over the cloud | `#b3b2b9` | `#99a5b8` | 1.18 |
  | normal foreground over the cloud | `#e5e0ef` | `#9293b8` | 2.29 |

  **Consequence for §5 and §6:** on this wallpaper the floor has already lifted slot 8 to about tone 73, not tone 45. The grey fails because one pane's ground spans about tone 24 to 67, and a palette solved against the mean cannot clear the bright patch. Raising slot 8's tone or its floor (§6) does not fix this case. The levers are the ground statistic (a bright percentile rather than the mean, which pulls the veil or polarity), the veil cap, the blur and frost strength, or a per-glyph halo. On this ground even the body foreground is 2.29:1.
- **Still unverified:** the light-theme palette on a device (this needs `night` changed on pong, which the developer has to allow), and the §6 tones run through `contrastTone`.
