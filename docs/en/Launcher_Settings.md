# Settings map

Open Settings by long-pressing inside the terminal and choosing **Settings**. You can also long-press
the app icon in another launcher and choose the **Settings** shortcut.

This map uses the exact top-level labels shown by v0.2.31. Use **Search settings** at the top when you
know what you want but not where it lives. Search indexes the preferences inside every destination,
so `fonts`, `ligatures`, or `Shizuku` can find the containing section.

## Appearance

Use this section for visible surfaces and colors:

- **Surface editor:** tune the dock, keyboard, status panel, terminal, and sessions while looking at
  the real home screen.
- **Terminal fonts:** install one of fourteen curated multi-face families with pinned SHA-256
  verification and visible license information, enable Nerd Font icons, choose ligature behavior,
  and adjust weight where supported.
- **Color mode:** follow the system, force light, or force dark mode.
- **Use wallpaper colors:** build the launcher palette from the Android wallpaper.
- **Interface colors:** choose whether the dock, status bar, app drawer, in-app keyboard and command
  palette follow the wallpaper palette or the terminal color scheme in
  `~/.termux/colors.properties`. Needs Android 11 or newer, and a scheme on disk — apply one from
  Termux:Styling first. See [Theming from a color scheme](#theming-from-a-color-scheme).
- **Terminal contrast:** choose Softer, Default, or Harder for the generated wallpaper palette.
- **Wallpaper:** show or hide the system wallpaper behind launcher surfaces.
- **Icon appearance:** monochrome icons, system or custom icon pack, and pinned-app icon behavior.

The font picker writes its managed selection to `~/.termux/fonts.d/10-launcher.conf`. **Use font.ttf
/ Termux:Styling** removes that one managed config; it does not delete your own `fonts.conf` or
downloaded families.

The launcher exports its resolved roles to `~/.termux/material-colors.sh` and `.properties` —
including container/on-container pairs, tertiary, error-container, and outline roles for prompts and
scripts — whether the palette came from the wallpaper or from a color scheme.

### Theming from a color scheme

Set **Interface colors** to *From terminal color scheme* and the whole interface is derived from
`~/.termux/colors.properties`, the file Termux:Styling writes. The scheme is the anchor, not a seed:
`background` becomes the surface, `foreground` becomes the text color, `color4` (or a distinctly
colored `cursor`) becomes the accent, `color1` the error color, `color8` the divider color. Only the
tones the scheme has no opinion about — container elevations and their text colors — are derived, as
a lightness ladder off the background with contrast repaired afterwards.

Changing a scheme in Termux:Styling recreates the activity and repaints everything. The setting is
unavailable below Android 11, where the palette cannot be loaded into a running activity; the
terminal itself still follows the scheme there.

Any derived color can be overridden in `~/.termux/launcher-theme.properties`, one token per line:

```properties
# accent from the scheme's yellow instead of its blue
primary                = color3
surface_container_high = lighten(surface, 0.08)
outline_variant        = mix(on_surface, surface, 0.78)
scrollbar              = alpha(on_surface_variant, 0.3)
inverse_primary        = #d79921
```

A value is a hex color, a scheme key (`background`, `foreground`, `cursor`, `color0`-`color15`),
another token name, or `lighten` / `darken` / `mix` / `alpha` over any of those. Amounts accept
`0.25` or `25%`. Unparsable lines are ignored and logged; the rest of the file still applies.

The tokens are `surface`, `surface_dim`, `surface_bright`, `surface_container_lowest`,
`surface_container_low`, `surface_container`, `surface_container_high`, `surface_container_highest`,
`on_surface`, `on_surface_variant`, `outline`, `outline_variant`, `scrollbar`, `primary`,
`on_primary`, `primary_container`, `on_primary_container`, and the `secondary`, `tertiary` and
`error` families spelled the same way, plus `inverse_surface`, `inverse_on_surface` and
`inverse_primary`.

## Terminal & status

Use this section for terminal geometry and the top row:

- **Customize status appearance:** status blur, opacity, grain, radius, and shared surface shape.
- **Full screen:** hide Android system bars while using the launcher.
- **Adjust for on-screen keyboard:** keep terminal content above the Android keyboard.
- **Split-pane controls:** enable native windows, panes, and their tmux-style shortcuts. Turning this
  off returns to single-pane compatibility behavior and closes secondary panes, so finish their work
  first.
- **Clock style** and **Use 12-hour time**.
- **CPU usage**, **Memory usage**, and **Weather** status cards.
- **Media and pinned notifications** and their essential notification rules.

The expanded status panel's clock opens Android's clock app and its cog opens Settings. Window pills
also show CPU-based working state and bell-based attention state; those indicators need no toggle.

Weather requires location permission. Tap the weather value in the status row for details and the
Open-Meteo attribution.

## Keyboard & input

- **On-screen keyboard:** built-in terminal keyboard, Android keyboard, or none.
- **Hide the on-screen keyboard:** hide it while a physical keyboard is connected.
- **Customize keyboard appearance:** live size, spacing, radius, and color tuning.
- **Theme**, **Keyboard colors**, and **Typeface**.
- **Extra keys:** choose editing and navigation keys shown on the built-in layout.
- **Custom layout:** load `~/.termux/keyboard/layout.xml`.
- **Layout documentation** and **Supported key values**.
- **Haptic feedback** and **Keypress sound**.

Keyboard height is remembered separately for portrait and landscape.

## Launcher & apps

- **Customize dock appearance:** shared surface shape and live dock tuning.
- **Show pinned apps row** and **Edit pinned apps**.
- **Alphabets row:** show or hide the A–Z index.
- **Launcher haptics** and **Notification dots**.
- **Most-used apps page:** add a page ranked by launcher usage.
- **App search prefix:** character that starts app filtering at an idle shell prompt; `%` is the
  default.
- **Reset usage ranking:** clear learned rankings without changing pins.
- **Set as default launcher:** open Android's default Home app screen.
- **Double tap A–Z Row to lock screen:** choose and configure the available lock backend.
- **Show in Recents when not the default launcher**.

## Services & permissions

This page reports real availability and offers the appropriate fix or manage action:

- **TAI · Termux AI:** local AI service, model catalog, runtime, endpoint, and token settings.
- **Shizuku:** optional privileged backend connection. See the [Shizuku guide](Shizuku.md) for
  setup, feature fallbacks, and troubleshooting.
- **Termux:API:** configuration appears when the matching add-on is installed.
- **Files and media**, **Notification access**, **Accessibility service**, **App notifications**, and
  **Other app permissions**.

An **Action needed** badge is a status, not an app failure. Tap **Fix** only if you want the feature
that needs that Android permission or service.

## Advanced & diagnostics

Use this section when investigating a problem:

- log level and optional terminal key-event logging;
- plugin and crash-report alerts;
- privileged-backend test;
- **Copy diagnostics**, **Export logs**, and **Clear logs**; and
- low-level terminal, renderer, backend, and plugin developer options.

Diagnostic output can contain device, package, and path information. Review it before posting it
publicly.

## About & support

This section links to documentation, GitHub feedback, source code, version/build information,
open-source licenses, and donations.

When reporting a bug, include the exact version and edition, Android version, device architecture,
the shortest reproduction steps, and diagnostics if they are relevant.
