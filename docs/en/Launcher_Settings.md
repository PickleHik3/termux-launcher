# Settings map

Open Settings by long-pressing inside the terminal and choosing **Settings**. You can also long-press
the app icon in another launcher and choose the **Settings** shortcut.

This map uses the exact top-level labels shown by v0.2.31. Use **Search settings** at the top when you
know what you want but not where it lives. Search indexes the preferences inside every destination,
so `fonts`, `ligatures`, or `Shizuku` can find the containing section.

## Layout

Use this section to arrange what each place shows and where. A place is Home (the widget grid),
Terminal, or Display (once the Linux display is on); tabs at the top pick which one the rows below
describe, and a Portrait/Landscape pill picks the orientation, since an arrangement can differ
between the two. A miniature shows the result, and tapping a part of it jumps to that row.

- **Status bar** and **Apps row**: which edge the row stands on, or hidden. **Alphabets row** shows
  or hides the A–Z index below the pinned apps, and only while the apps row is along the bottom.
  **Extra keys**: where the terminal's extra keys stand, or hidden.

The status bar can stand on any of the four edges and is never hidden, so the swipe that moves
between places always has somewhere to live. At the top it is the bar you already know. At the
bottom it sits on the dock and shares its look, growing upward when you open it. On the left or the
right it becomes a narrow column: the place badge, one chip per window, and the system readings
stacked down it, with the clock written hour over minutes when the column is open. Whichever edge
it stands on, a swipe along the bar moves between places — sideways on a bar across the top or
bottom, up and down on a column, where Home is above Terminal and Display below it — and a swipe
across the bar opens and closes it. A column shares its edge with the pinned apps and the extra
keys when those stand there too: the status bar takes the top of that edge and the rest follows
underneath.
- **Keyboard on enter**: whether the on-screen keyboard comes back the way you left it, opens, or
  stays closed when you switch to this place. Remembered per place, the same in both orientations.
- **Grid columns** and **Grid rows** (Home only): how many widgets fit across and down a page, set
  separately for portrait and landscape; widgets that no longer fit a smaller grid move to free
  space or a new page rather than being dropped.
- **Look of this place**: opens the surface editor for what is on screen here.

## Appearance

Use this section for visible surfaces and colors:

- **Surface editor:** tune the dock, keyboard, status panel, and terminal while looking at the real
  home screen. Tap the floating palette to style every surface at once, or tap a surface to style
  it on its own.
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
- **Automatic tiling:** new windows start in the `dwindle` layout — every new pane halves the focused
  one along its longer side, a dragged pane takes the half you drop it on. Off by default; any window
  can still pick a layout with `Ctrl+Alt+L`.
- **Focused pane grows:** the pane you tap (or focus with `Alt+Arrow`, or an agent focuses) takes
  most of the room and the others slide aside, focus.nvim-style. Tap between an agent's pane and the
  pane it drives to switch which one is big. Turning it off puts every divider back to 1:1.
- **Let scripts open panes:** whether `launcherctl pane …` and the `/v1/panes` routes of the local
  API may open and drive panes. On by default; off answers those routes with 403.
- **Clock style** and **Use 12-hour time**.
- **CPU usage**, **Memory usage**, and **Weather** status cards.
- **Media and pinned notifications** and their essential notification rules.
- **Battery → Lazy mode:** stop the launcher animating while you are only looking at it. The clock
  swaps its digits instead of folding them, a working window's rim holds lit instead of breathing,
  the status readings sample less often, and the weather icon rests on its last frame. Nothing on
  screen repaints until something actually changes.

**Please try Lazy mode.** Without it the launcher redraws every frame the panel offers, purely to
animate the clock's seconds — that is a real battery cost for something nobody is watching most of
the time. The intent is to make it the default once it has been through enough hands; what that
needs is people running it on other devices and reporting anything that looks stuck, stale, or
wrong — a clock that stops updating, a status reading that freezes, a rim that never lights.
[Open an issue](https://github.com/PickleHik3/termux-launcher/issues) if you find one.

The expanded status panel's clock opens Android's clock app and its cog opens Settings. Window pills
also show CPU-based working state and bell-based attention state; those indicators need no toggle.

Weather requires location permission. Tap the weather value in the status row for details and the
Open-Meteo attribution.

## Keyboard & input

- **On-screen keyboard:** built-in terminal keyboard, Android keyboard, or none.
- **Hide the on-screen keyboard:** hide it while a physical keyboard is connected.
- **Customize keyboard appearance:** live size, spacing, radius, and color tuning.
- **Bottom padding:** lift the bottom key row away from the edge of the screen. The surface
  editor sets the same padding by dragging the pill under the last key row.
- **Theme**, **Keyboard colors**, and **Typeface**.
- **Edit extra keys:** the terminal key row, previewed as it will look. Add common keys
  such as CTRL with one tap, search for any other key or launcher action, drag keys to
  reorder them, give a key a swipe-up action and a label, and start from presets including
  the classic Termux row. Page two of the row is edited on the same screen. **Go to Widgets**,
  **Go to Terminal**, **Go to Display** and **Mouse mode** are among the actions a key can carry.
- **Extra keys:** choose editing and navigation keys shown on the built-in layout.
- **Custom layout:** load `~/.termux/keyboard/layout.xml`.
- **Learn where you tap:** off by default. The keyboard learns where your taps land on each key
  and nudges presses near a boundary toward the key you usually mean. Only letters, digits and
  punctuation are ever moved; Enter, Backspace, Ctrl and the other action keys are not. **Forget
  learned taps** clears what it has learned. It stores per-key averages only, never what you typed.
- **Layout documentation** and **Supported key values**.
- **Haptic feedback** and **Keypress sound**.

Keyboard height is remembered separately for portrait and landscape.

## Display

The Linux display has its own page: the **Linux display** switch runs a Linux desktop or X11 apps
as the third place of the home screen — see [The Linux display](X11_Display.md). Under it sit touch
mode, resolution, text size, clipboard sharing, whether Linux apps are listed in the app drawer,
the window manager started with the display, the mark on the Display place's badge in the status
bar, starting the display with the launcher, the start command, pointing new shells at the
display, two compatibility switches, and what your GPU can do for Linux apps. Where the extra keys
stand while the display is showing, and everything else about its arrangement, lives on the
**Layout** page's Display tab once the switch above is on. To have the phone try every graphics
profile and keep the best, run `termux-x11-gpu-setup` in a shell.

## Launcher & apps

- **Customize dock appearance:** shared surface shape and live dock tuning. Where the pinned apps
  stand, the alphabets row, and the widget grid's size are all on the **Layout** page now, one place
  and orientation at a time.
- **Edit pinned apps**.
- **App drawer:** swipe down on the pinned row to open it; choose the drawer layout. **Open the
  keyboard with the drawer** brings the keyboard up as the drawer opens; **Search with the Android
  keyboard** searches through your Android keyboard, with its suggestions and swipe typing. With the
  categories layout, **Sort apps into categories** and **Re-run categorization** appear here too; the
  re-run row counts the apps installed since the last run, and the drawer mentions them once when
  more than five are waiting.
- **Widget pane:** keep a page of home-screen widgets beside the terminal.
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
