# Settings map

Open Settings by long-pressing inside the terminal and choosing **Settings**. You can also long-press
the app icon in another launcher and choose the **Settings** shortcut.

This map uses the current top-level labels. Use **Search settings** at the top when you know what you
want but not where it lives. Search indexes the preferences inside every destination, so `fonts`,
`ligatures`, or `Shizuku` can find the containing section.

## Mode

The first row of Settings, above the pages, says what this install is: **Terminal** (just the
terminal), **Terminal + Home** (apps, widgets and the app drawer) or **Terminal + Home +
Display** (run Linux desktop apps too). Tap it to pick one. A mode is a preset, not a
lock: it sets the pinned apps row, alphabets row, app drawer, widget pane, Recents behaviour and
the Linux display switch, and each of those stays editable on its own page afterwards. Once you
have moved one of them off the preset the row reads **Custom**. Picking **Terminal** while Termux
Launcher is your phone's home screen offers to choose another home app; nothing is forced.

A place that is off is not just hidden: a terminal-only install runs no app catalogue, no widget
host and no display, and the Linux display is only built while its switch is on.

## Layout editor

Open **Layout** from a corner tab, the long-press menu, or the **Layout** row at the top of
Settings, beside **Look**. Home, Terminal and Display share one layout, so whatever you arrange
lands on all three; the layout canvas shows the place you opened it on. A Portrait/Landscape
toggle above the canvas (a phone standing up, a phone on its side) switches which orientation you
are arranging, since each orientation has its own layout; the real screen behind the editor
follows what you change only for the orientation you are actually holding the phone in. Done and
Undo let you keep or throw away everything you changed in that visit.

There are no rows of settings under the canvas: you move things by dragging them, hide them by
dropping them in the tray, and size them by their handles.

- **Move.** Every bar — **Status bar**, **Apps row**, **A–Z index** and **Extra keys** — is
  pressed anywhere and moved to the edge you want it on, any of the four, in either orientation;
  a tap only selects it. Dropping a bar between two others on the same edge sets the order they stack in.
  The A–Z index rides the pinned apps row while the two share an edge; dropped on another edge it
  stands on a bar of its own.
- **Under the keyboard.** Where the canvas shows the keyboard, it also offers a slot below it:
  drop the apps row, the A–Z index or the extra keys there and they stand under the keyboard
  instead of over it, in the order you stack them. With the keyboard down or switched off they
  simply sit at the bottom of the dock with the other rows, with no gap between them. The status
  bar always stands over the keyboard.
- **Hide and bring back.** Drop a bar into the tray under the phone to put it away; while a bar is
  lifted the tray reads **Drop here to hide**. Each hidden element is a chip in the tray with a
  struck-through eye; tap it and the element comes back to the edge it was hidden from.
- **Keyboard on/off.** Drag the keyboard itself off the phone and into the tray to switch it off;
  its chip in the tray switches it back on. Off, nothing raises it — not a tap on the terminal,
  not a text field — until you turn it on again, which makes it the setting for a hardware
  keyboard or a touch-driven full-screen program. It is the same switch as **Keyboard on/off** in
  the command palette and on the extra-keys row, and unlike the rest of the layout it is one
  switch for both orientations and every place.
- **Select and resize.** Tap anything on the canvas to select it: it gets one outline, and the
  pieces with a size get a handle. Drag the dock's inner edge for its height; the keyboard's top
  edge for its height, and the bottom of its keys upward for the padding under its last row; and
  on Home the round handle on the corner of the first cell for the widget grid, which snaps to
  whole cells (dragging it out makes the cells bigger and fewer). While you hold a handle it shows
  the size in real units — the dock or keyboard in dp as the launcher lays it out, the padding in
  dp, the grid in cells — and each is set separately per orientation.
- **Keyboard type.** With the keyboard selected, three chips beside it pick docked, floating or
  split, for the orientation on the toggle. The chips go when you select something else.

The A–Z index is on, somewhere, or in the tray: there is no longer a Minimised form, and an index
that was minimised comes back as the bar it folded from.

The status bar can be hidden like the rest, which is how a full-screen layout is built by hand:
with it away the clock, the weather and the window pills go with it, the content takes its band,
and the corner tab is still the way into Layout to bring it back. Shown, it can stand on any edge.
At the top it is the bar you already know. At the bottom it sits on the dock and shares its look,
growing upward when you open it, with the clock at its foot and the window pills along its upper
edge. On the left or the right it becomes a narrow column: the place badge, one chip per window,
and the system readings stacked down it, with the clock written hour over minutes when the column
is open. Whichever edge it stands on, a drag across the bar opens and closes it; moving between
places is the border drag, on every place, and never the bar's own gesture. A column shares its
edge with the pinned apps and the extra keys when those stand there too, in the order you stacked
them.

The card is a sheet from the bottom of the screen, like the Appearance card. Its header — the
title, Undo, Discard and Done — stays at the top. It rests low enough to leave the top of the real
screen in view; pull it up by the handle or the header and it grows toward the top of the screen,
and a tap on the handle does the same. A firm pull down past its resting size closes the editor the
way Back does: straight away when nothing has changed, and with the keep-or-discard question when
something has. While you drag a bar or hold a handle, the card stays still.

The keyboard itself remembers where it was: Terminal and Display each come back with the keyboard
up or down the way you left them, and Home always comes back with it down. On Home the keyboard
opens over the page rather than shrinking it, so the widgets keep their places; a text field inside
a widget opens the Android keyboard, and the keyboard key on the extra keys row opens the built-in
one.

Minimal mode is a preset of the same elements: with it on, the status bar, the pinned apps, the
A–Z index and the extra keys are put away and the keyboard is put down, and turning it off brings
each one back exactly where the layout has it. Anything you want left on screen in minimal mode is
therefore arranged here — a terminal with only the A–Z index and the keyboard, say, is that layout
with the other bars hidden, and minimal mode adds nothing to it. Widgets
that no longer fit a smaller grid move to free space or a new page rather than being dropped.

## Look

Use this section for visible surfaces and colors:

- **Appearance** (from a corner tab): the launcher scaled into a frame, with a Look slider under
  it. Slide between **Clear**, **Mist**, **Tint** and **Solid**; the last stop, **Custom**, lets you
  tap an element in the frame — the dock, the keyboard, the status bar, a pane — and tune its
  Darkness, Legibility, Blur, Key corners, or Soft wallpaper with Dim. Home, Terminal and Display
  wear the same look, so what you change here changes all three. **Undo** steps back and **Done**
  keeps your changes.
  The keyboard's **BG opacity** applies to the keyboard docked under the terminal — a floating,
  split, or overlaying keyboard is a solid panel and ignores it — while its **Edges** apply to
  every keyboard.
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
- **Tools that follow the terminal colours:** pick the command-line tools that should be recolored
  with the terminal. See [Tools that follow the terminal colours](#tools-that-follow-the-terminal-colours).
- **Wallpaper:** show or hide the system wallpaper behind launcher surfaces.
- **Icon appearance:** monochrome icons, system or custom icon pack, and pinned-app icon behavior.
- **Keyboard look:** one **Keyboard theme** page (live preview, **Theme**, colors and **Typeface**) for the built-in keyboard,
  and **Customize keyboard appearance** for live size, spacing, radius, and color tuning. These
  rows are only enabled while the built-in keyboard is the chosen input method on the **Keyboard**
  page. The padding under the keyboard's last key row is set in the **Layout** editor, by the
  handle on the bottom of the keys.

The font picker writes its managed selection to `~/.termux/fonts.d/10-launcher.conf`. **Use font.ttf
/ Termux:Styling** removes that one managed config; it does not delete your own `fonts.conf` or
downloaded families.

The launcher exports its resolved roles to `~/.termux/material-colors.sh` and `.properties` —
including container/on-container pairs, tertiary, error-container, and outline roles for prompts and
scripts — whether the palette came from the wallpaper or from a color scheme.

Beside them, a wallpaper palette also writes `material-colors-dark.sh` / `.properties` and
`material-colors-light.sh` / `.properties`: the same keys for each mode, so a script can dress
itself for the mode the phone is not in yet. The two files without a mode in the name stay the
palette in use right now.

### Tools that follow the terminal colours

Pick a tool in this list and the launcher writes its theme file whenever the palette changes, then
wires it into that tool's own config. Turning one off puts the config back as it was. Tools that can
reload live do; the rest pick the colors up the next time they start. Built in: Starship, Helix,
tmux, bat, Yazi, fzf, lazygit, Oh My Posh, Neovim, fish and herdr, a terminal workspace manager whose
accent, panes and status line follow along. The fish entry colors the command line you type on.

The palette itself keeps up on its own: it re-renders whenever your wallpaper, Material colour, or
system dark/light mode changes, so a tool on this list never shows yesterday's contrast.

You can add your own. A template is a directory in `~/.termux/theme-templates/<id>/` holding a
`template.properties` manifest, the file to render, and its hooks:

```properties
name     = Starship
summary  = Prompt palette
input    = starship.toml
output   = ~/.config/launcher-material/starship.toml
post_hook = apply.sh
undo_hook = undo.sh
```

Templates in that directory apply because they are there — they need no switch — and an id that
matches a shipped template replaces it. `output` understands `~`, `$VAR` and `${VAR}`, with
`XDG_CONFIG_HOME` and `XDG_CACHE_HOME` defaulting to `~/.config` and `~/.cache`.

The input file is plain text with `{{ colors.<token>.<mode>.<format> }}` placeholders — for example
`{{ colors.primary.dark.hex }}`. Tokens are the key names in `~/.termux/material-colors.properties`,
mode is `default` for the palette in use and `dark` or `light` for those modes' own colors (a
palette with only one mode gives it for all three), and the formats are `hex`,
`hex_stripped`, `rgb`, `rgba`, `red`, `green` and `blue`. `{{ mode }}` renders `dark` or `light`.
Everything else in the file is left exactly as written, so a Go or Lua template survives intact. An
unknown token or format skips that template and logs why; nothing is written.

An optional `setup_hook` names a script for a tool that is only switched on by a line in your shell
startup file, which the launcher never edits for you. Turning such a tool on offers you the command
— `bash "<template dir>/<setup_hook>"` — to copy and run in the terminal once; it adds that line.

`post_hook` runs after the file is written, `undo_hook` when the template is turned off or removed.
Both are run as `bash <hook>` from the template directory with `TERMUX_THEME_ID`, `TERMUX_THEME_DIR`,
`TERMUX_THEME_OUTPUT` and `TERMUX_THEME_MODE` set, and are stopped if they take longer than thirty
seconds. Write them to be repeatable: add one include line or a marker block, change nothing when
nothing changed, and have the undo remove exactly what the apply added. The theme name used
throughout is `launcher-material`.

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

- **Fancier Glass** has no switch any more: every glass surface bends the wallpaper at its edge, as
  deep as the chosen Look says, wherever the device can. It needs Android 13 and a wallpaper set
  with the in-app wallpaper picker (or `launcherctl wallpaper set`); with any other wallpaper the
  glass stays flat. Lazy mode, battery saver and reduced motion switch the extra motion off.

## Terminal

Use this section for terminal geometry, panes, and how the launcher behaves as an app:

- **System keyboard compatibility:** workaround for Android system keyboards, padding the layout so
  the terminal and key rows stay visible above the on-screen keyboard.
- **Full screen:** hide Android system bars while using the launcher.
- **Show in Recents when not the default launcher**.
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
- **Clipboard Cleanup:** drop trailing spaces from copied lines, and the trailing newline from a
  one-line paste, so a copied command runs again cleanly instead of leaving a blank line behind it
  or waiting on an extra newline. On by default.

## Status bar

Use this section for the top row's readouts. The clock's face and position, and the bar's
surface — blur, opacity, grain, and radius — are set in Appearance, opened from a corner tab or the
long-press menu, and are the same on every place.

- **Use 12-hour time**.
- **CPU usage**, **Memory usage**, and **Weather** status cards.
- **Media and pinned notifications** and their essential notification rules. A rule pins the
  notifications that match its app package, its keywords, or both. Each rule has a switch: off keeps
  the rule but stops it pinning anything. **Mute this rule** on a card turns that switch off.

A pinned card shows the sender, the message, how long ago it arrived, and a count when several
messages from one conversation are folded into it. Tap a card to open the notification. Swipe it
left or right to dismiss it; **Undo** stays in its place for 4 seconds. Long-press a card to read
the whole message and choose **Open**, **Dismiss**, or **Mute this rule**. With two cards or more,
swipe up or down on them to see the rest. See [Pinned notifications](Pinned_Notifications.md).

The expanded status panel's clock opens Android's clock app and its cog opens Settings. Window pills
also show CPU-based working state and bell-based attention state; those indicators need no toggle.

Weather requires location permission. Tap the weather value in the status row for details and the
Open-Meteo attribution.

## Keyboard

- **On-screen keyboard:** built-in terminal keyboard, Android keyboard, or none.
- **Hide the on-screen keyboard:** hide it while a physical keyboard is connected.
- **Edit extra keys:** the terminal key row, previewed as it will look. Add common keys
  such as CTRL with one tap, search for any other key or launcher action, drag keys to
  reorder them, give a key a swipe-up action, a label and a colour, and start from presets
  including the classic Termux row. Page two of the row is edited on the same screen. **Go to
  Widgets**, **Go to Terminal**, **Go to Display** and **Mouse mode** are among the actions a key
  can carry.
- **Key colours:** a key can be given one of eleven colours, or left with the row's own styling.
  Nine of them follow your theme, so they change with dark mode and with the colours the launcher
  takes from your wallpaper; black and white stay as they are. Pick a colour in the key editor, or
  open the Appearance editor, touch the keyboard, and tap the key itself. Out of the box the three
  place keys are coloured, so Widgets, Terminal and Display are easy to tell apart.
- **Keys that do nothing where you are get dimmed.** The key row follows you between Widgets,
  Terminal and Display, and a key with nothing to act on goes faint and stops responding until you
  move somewhere it works. Nothing is removed or rearranged, so the row always looks the same. On
  **Terminal** every key works. On **Display** everything works except keys that manage panes,
  windows and sessions. On **Widgets** you keep the place keys, the keyboard key, the sessions key
  and anything belonging to the launcher itself — settings, help, the command palette, the layout
  and appearance editors, the wallpaper, launching an app — while typing keys, modifiers, Paste,
  Scroll and the terminal's own tools are dimmed.
- **Extra keys:** choose editing and navigation keys shown on the built-in layout.
- **Custom layout:** load `~/.termux/keyboard/layout.xml`.
- **Learn where you tap:** off by default. The keyboard learns where your taps land on each key
  and nudges presses near a boundary toward the key you usually mean. Only letters, digits and
  punctuation are ever moved; Enter, Backspace, Ctrl and the other action keys are not. **Forget
  learned taps** clears what it has learned. It stores per-key averages only, never what you typed.
- **Floating keyboard width**, **Floating keyboard height** and **Split keyboard gap**: how much
  of the screen a floating keyboard takes, how tall it is, and how far apart the halves of a split
  one sit. All three are set separately for portrait and landscape; which type a place uses is
  chosen in the **Layout** editor. A floating keyboard can also be resized in place by dragging the
  handle in its bottom-left corner: out to the left makes it wider, up makes its rows taller, and
  the edges you are not holding stay where they are. It writes the same two values.
- **Layout documentation** and **Supported key values**.
- **Voice input:** **Speech engine** (**Android system** or **On-device**; unpicked, it goes
  on-device once a speech model is installed), **Voice language**, **Polish dictation with local
  model** (on by default) with its **Cleanup
  model** and **Cleanup level**, **Pause that ends a phrase**, **Silence auto-stop**, **Voice
  sounds**, and **Speech model**, which also holds the **Voice output** voice and speed for Read
  aloud. See [Voice input](Voice_Input.md) and [Text to speech](Text_To_Speech.md).
- **Haptic feedback** and **Keypress sound**.

Whether the keyboard is docked, floating or split is picked in the **Layout** editor: select the
keyboard and three chips beside it choose the type, for the orientation on the toggle.

The keyboard's look — theme, colors and typeface — moved to the **Look** page,
alongside the launcher's other visual choices; **Keyboard theme** and
**Customize keyboard appearance** are greyed out here while the on-screen keyboard is not the
built-in one. Keyboard height is remembered separately for portrait and landscape.

## Apps

- **Edit pinned apps**. (What this app is for moved to the **Mode** row at the top of Settings.)
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
- **Play the tour again:** replay the first-launch overlay tour, card one onward, on the home
  screen behind Settings.
- **Double tap A–Z Row to lock screen:** choose and configure the available lock backend.

Where the pinned apps stand, the alphabets row, and the widget grid's size are all in the **Layout**
editor now, one place and orientation at a time, and its dock look is tuned in Appearance, opened
from the long-press menu on that place.

## Linux display

The Linux display has its own page: the **Display** switch runs a Linux desktop or X11 apps
as the third place — see [The Linux display](X11_Display.md). It takes effect
as soon as you return to the launcher: on, the Display place appears; off, it goes, and a display
that is running asks whether to stop first. The switch also moves **Mode** between Terminal +
Home and the display mode. Under it sit touch
mode, **OSK auto-show** — the keyboard opens when you tap a text field on the
display and closes when you tap elsewhere, on by default and available in Touchscreen touch mode —
resolution, text size, clipboard sharing, whether Linux apps are listed in the app drawer,
**Setup GUI Apps** to install some — from the Termux X11 repo or from a full Linux inside it, and
what your GPU can do for them — and **Hide GUI Apps** to choose which of them stay out of the
drawer (see [Linux apps from a distro](Linux_Apps_From_A_Distro.md)),
the window manager started with the display, the mark on the Display place's badge in the status
bar, starting the display with the launcher, the start command, pointing new shells at the
display and two compatibility switches. A line at the foot of the page says what arranges the
windows, and that a whole desktop arranges its own. Where the extra keys
stand while the display is showing, and everything else about its arrangement, lives in
the Layout editor, opened on the display once the switch above is on. To have the phone try every graphics
profile and keep the best, run `termux-x11-gpu-setup` in a shell.

## On-device AI

**On-device AI**, right after Display in the settings root, opens the local AI service: the
**Model centre** (chat models, speech models and the voice model), runtime, endpoint, and token
settings. See [On-device AI](On_Device_AI.md).

## Services & permissions

This page reports real availability and offers the appropriate fix or manage action:

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
- privileged-backend test; and
- **Copy diagnostics**, **Export logs**, and **Clear logs**.

Diagnostic output can contain device, package, and path information. Review it before posting it
publicly.

## About & support

This section links to documentation, GitHub feedback, source code, version/build information,
open-source licenses, and donations.

When reporting a bug, include the exact version and edition, Android version, device architecture,
the shortest reproduction steps, and diagnostics if they are relevant.
