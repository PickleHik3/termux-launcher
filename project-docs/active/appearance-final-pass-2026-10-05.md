# Appearance final pass (2026-10-05)

Branch `feat/appearance-final-pass`, built from dev f3dafe07a. Not merged to dev: a second agent is
building the in-app live wallpaper in parallel, and this branch must stay pluggable for it (see §7).

Source: the developer's review of the four Look screenshots on pong (`~/Shared/tl-themes/`) and a
list of defects and new controls for the one unified Appearance menu.

## 1. Findings from the screenshots and the code

- **Clear and Mist terminal panes are the same dark-grey slab.** The pane's legibility veil
  (`ChromeInk.terminalPane` → `OnGlass.resolveFixedInk`) has no ceiling: it searches alpha 1–255 until
  the palette foreground reaches 4.5:1 and the dim foreground 3.0:1. Clear (terminal opacity 8%)
  therefore gets almost the whole gap as veil; Mist (60% obsidian) gets little. Both land on the same
  luminance. Clear also blurs at 44dp (near the 48 cap), so the pane is a featureless wash. The
  chrome bands cap their veil at 0.55 and re-tone the ink instead; the pane does neither.
- **Tint is not "material colours".** It uses the obsidian (#161822) tint, like Mist. Only the name
  differs.
- **The pane frost filter keys off the dock's opacity**, not the terminal's (`TermuxActivity`
  `paneSurfaceStyle`).
- **Terminal contrast moves the status bar.** `terminal_contrast_level` is read by both the palette
  (`MaterialTerminalColorScheme`) and `LegibilityLevel`, which scales every chrome band's target
  (3.0 / 4.5 / 7.0). With wallpaper colours off the terminal ignores it but the chrome still veils.
- **A custom theme is not followed.** With `terminal_dynamic_colors_enabled` off,
  `refreshMaterialTerminalColorsIfNeeded` returns early, so `~/.termux/colors.properties` is re-read
  only on activity create or a style reload.
- **Keyboard theme page** is code-built (`KeyboardColorSchemeFragment`): Theme segments, Colors
  (status, swatch grid, palette card "Editing <role>"), Typeface row.
- **Status bar chrome escapes the token layer:** session chip fill is a static
  `R.color.termux_surface_panel_high`; there is no `termuxColorTertiary` alias so widgets use raw
  `colorTertiary`; the clock resolves roles once in its constructor and never through ChromeInk;
  Flip's rule colour differs between the clock and the slot's hairline; the column chip pairs
  onSecondaryContainer text with no container fill. The expanded clock only ever downsizes: with
  media or notifications visible it drops to its 26sp COMPACT form inside a 68dp slot.
- **Lock wallpaper flash on open:** `beginSession` loads an editor wallpaper and
  `setEditorWindowOpaque(true)` swaps the window background on a `windowShowWallpaper` window (a WM
  relayout while the wallpaper shows); `setCovered(true)` drops the Home living still to its still.

## 2. Looks (decided here)

| Look | Blur | Opacity | Grain | Tint | Terminal override | Depth |
|---|---|---|---|---|---|---|
| Clear | 4 | 10 | 4 | scheme | none (10) | subtle 4/10/18 |
| Mist | 25 | 60 | 8 | obsidian | none | medium 9/20/18 |
| Tint | 10 | 46 | 10 | **material** (new) | none | subtle |
| Solid | 0 | 92 | 0 | scheme | none | off |

- **material tint** = the scheme surface blended toward `colorPrimaryContainer` at 35%, with the
  scheme's `colorSurfaceTint` wash at 0.14 (M3 tonal elevation level 5). Stored as
  `surface_glass_tint = material`.
- **Pane veil ceiling:** `min(0.55, max(0.20, opacity + 0.15))`. Clear's pane may be veiled to 25%,
  Mist and Tint to 55%, as the chrome bands are. Where the cap binds the resolution reports it, so
  the pane can later re-tone (not in this pass).

## 3. Terminal contrast = palette only

- `LegibilityLevel` no longer reads `terminal_contrast_level`; chrome bands stay at the default
  targets. `terminal_contrast_level` drives only `MaterialTerminalColorScheme`:
  - **Softer**: pastel. Chroma clamp 18–30, normal tone 84/46 (dark/light), bright 92/34, fg ratio
    4.5, ansi 3.5, bg tone 14/94.
  - **Default**: the system's Material scheme as declared, unchanged from today.
  - **Harder**: punchy. Chroma clamp 44–72, normal tone 78/38, bright 90/28, fg ratio 10, ansi 6,
    bg tone 6/98 (no darkening beyond today's Harder background).
- With wallpaper colours off, `~/.termux/colors.properties` is re-read on resume, configuration
  change and the editor's palette refresh. The Contrast control stays disabled in that mode.

## 4. Keyboard theme page

Theme section removed (`in_app_keyboard_theme` follows the system day/night; an imported scheme is
active whenever one exists). Order: live preview · **Hints** row (Corner labels, Bottom label) ·
**Keyboard** row (Background, Key fill, Border) · **Labels** row (Label colour) · swatch grid ·
bottom card = one capsule "Editing Key fill" (reset, edit) with the Font chooser as its second row.

## 5. Look editor controls (Custom stop)

Vertical M3 sliders (`android:orientation="vertical"`, 40dp track, legend text drawn inside the track
along its length, inverting where the active track covers it), spaced evenly across the sheet width
by count. Buttons sit above the sliders in a row of their own.

| Selection | Buttons above | Sliders |
|---|---|---|
| nothing (tap outside) | — | Blur · Grain · Opacity · Margin · Corner radius |
| Keyboard | Keyboard theme › | Blur · Grain · Opacity · Key radius · Key spacing |
| Dock | — | Blur · Grain · Opacity · Size · App icons |
| Terminal | Trail · Effect | Blur · Grain · Opacity · Contrast (3 stops) |
| Status bar | Clock (opens the face/position menu upward from the button) | Blur · Grain · Opacity |

The status bar is always expanded in Look mode (transient, not persisted; animates with the open).
Corner radius here is the element radius, never the key caps'. Soft wallpaper and Dim leave the
editor (prefs stay; the wallpaper Overview is their home).

## 6. Status bar

Expanded clock scales as one entity (time, AM/PM, seconds) up to fill the slot's column when media
or notifications share it. Colour roles: `termuxColorTertiary`/`OnTertiary`/`TertiaryContainer`/
`OnTertiaryContainer` aliases; session chip fill from `termuxColorSurfacePanelHigh` through ChromeInk;
AI dot gets the tertiary role; display and done marks move to roles; Flip rule unified; column chip
pairs `secondaryContainer` with `onSecondaryContainer`; clock re-resolves roles on theme change.

## 7. Wallpaper display contract for the live-wallpaper agent

The editor frame shows the real `wallpaper_backdrop` scaled with the root container; the separate
`EditorWallpaperView` and the opaque-window swap are gone. Live content plugs in through the existing
`WallpaperBackdropView.setLiveShader` / `GeneratedWallpaperHost` path and needs no editor change.
