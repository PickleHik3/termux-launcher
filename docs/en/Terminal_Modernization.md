---
page_ref: /docs/apps/termux-launcher/terminal-modernization.html
---

# Modern terminal guide

Termux Launcher includes native sessions, windows, recursive split panes, searchable actions and
sessions, durable workspaces, configurable keymaps and fonts, richer terminal protocols, and
diagnostic tools. These features run directly in the Termux terminal; tmux is still supported but
is not required.

This guide describes features that are implemented in the current project. Items listed under
[Current limitations](#current-limitations) are not implemented yet.

## Start here

The terminal hierarchy is:

```text
Session
└── Window
    ├── Tiled pane tree
    │   ├── Pane (one shell/PTTY)
    │   └── Pane (one shell/PTTY)
    └── Floating panes
```

- A **session** is a drawer entry. It may contain several windows.
- A **window** is one workspace inside a session. Only the selected window is visible.
- A **pane** is one live shell inside a window. Panes can be split recursively in either direction.

Split panes are enabled by default. To restore traditional single-pane Termux behavior, open
**Settings → Terminal & status → Split-pane controls** and turn the controls off. This closes
secondary panes and disables window/pane commands, so finish or save work in those shells first.

The fastest way to discover commands is the **Command palette**:

- Long-press the terminal, choose **Command palette**, and search by name or description.
- On a hardware keyboard, press `Ctrl+Alt+Shift+P`.
- The built-in chord `Ctrl+Alt+Space`, then `P`, opens the same palette.

Unavailable actions stay visible but are disabled with a reason, for example when there is no text
selection. Actions that can destroy or disclose data ask for confirmation.

The palette is the complete argument-free UI surface:

| Category | Available operations |
|---|---|
| Pane | Split horizontally/vertically, float or dock, cycle automatic layouts, equalize, rotate, terminate focused pane |
| Window | Create, close, next, previous, and rename |
| Session | Create, browse, clone with CWD, next, previous, close, rename, and rename any session by index |
| Terminal | Keyboard/toolbar toggles, font size, URL picker, hints, scrollback search, prompt navigation, sharing, and reset |
| Clipboard | Paste and copy selected text |
| Appearance | Wallpaper picker/toggle, cursor-trail toggle, and Glass Lab |
| App | Settings destinations, drawer controls, command palette, action sheet, and key inspector |

The long-press **Terminal action sheet** remains deliberately short: command palette, URL picker,
share transcript, wallpaper controls, Glass Lab, settings, reset terminal, and kill process. Use the
palette for the full searchable surface.

The palette is one glass rectangle with its search row at the top. The result count and current
breadcrumb sit at the right of that row. Six frequent-action keycaps sit below the results. The
open corner radius follows the dock capsule.

## Default keyboard shortcuts

Shortcuts match Android key codes, so they follow physical key positions rather than the character
produced by the current keyboard layout.

| Shortcut | Split panes enabled | Compatibility mode |
|---|---|---|
| `Ctrl+Alt+Enter` | New pane: split the focused pane along its longer side | Sent to the shell if unclaimed |
| `Ctrl+Alt+v` | Split vertically (side by side) | Paste |
| `Ctrl+Alt+h` | Split horizontally (stacked) | Sent to the shell if unclaimed |
| `Alt+Arrow` | Focus the pane in that direction | Sent to the shell when no pane lies that way |
| `Ctrl+Alt+Left` / `Ctrl+Alt+Right` | Previous/next window | Opens or closes the session drawer |
| `Ctrl+Alt+Up` / `Ctrl+Alt+Down` | Previous/next session | Previous/next session |
| `Ctrl+Alt+Shift+Arrow` | Resize the focused pane | Sent to the shell if unclaimed |
| `Ctrl+Alt+c` | New window | New session |
| `Ctrl+Alt+x` | Close current window, after confirmation | Sent to the shell if unclaimed |
| `Ctrl+Alt+[` / `Ctrl+Alt+]` | Previous/next window | Sent to the shell if unclaimed |
| `Ctrl+Alt+l` | Next automatic pane layout | Sent to the shell if unclaimed |
| `Ctrl+Alt+f` | Float or dock the focused pane | Sent to the shell if unclaimed |
| `Ctrl+Alt+r` | Rename current window | Rename current session |
| `Ctrl+Alt+R` (shifted) | Rename current session | Rename current session |
| `Ctrl+Alt+Shift+C` | New session | New session |
| `Ctrl+Alt+Shift+X` | Close current session, after confirmation | Sent to the shell if unclaimed |
| `Ctrl+Alt+n` / `Ctrl+Alt+p` | Next/previous session | Next/previous session |
| `Ctrl+Alt+1` … `Ctrl+Alt+9` | Activate that window in the session | Activate that drawer session |
| `Ctrl+Alt+Shift+1` … `Ctrl+Alt+Shift+9` | Activate that drawer session | Activate that drawer session |
| `Ctrl+Alt+k` | Toggle soft keyboard | Toggle soft keyboard |
| `Ctrl+Alt++` / `Ctrl+Alt+-` | Increase/decrease font size | Increase/decrease font size |
| `Ctrl+Alt+m` | Open terminal action sheet | Open terminal action sheet |
| `Ctrl+Alt+u` | Open terminal hints | Open terminal hints |
| `Ctrl+Alt+s` | Search scrollback | Search scrollback |

“Vertical split” means a vertical dividing line and therefore creates side-by-side panes.

## Touch and mouse

Touch handling differs from stock Termux: it is tuned for TUIs, not just shell prompts. Drags
scroll — translated to scroll-wheel events inside mouse-aware apps — and taps click when the app
tracks the mouse. **Press and hold briefly, then drag** to hold the mouse button down (a small
haptic marks the handoff): from there, select text in vim, drag tmux splits, or resize TUI panes
like a desktop mouse. A quick long-press without moving still opens ordinary text selection with
the copy toolbar, and pinch changes only the focused pane's font size.

## Panes, windows, and layouts

Use the default shortcuts or search the command palette for pane and window actions. Splitting starts
a fresh shell in the focused pane's working directory. Each pane retains its process, scrollback,
selection, and terminal state while you focus another pane or window.

Use **Float / dock pane**, `pane.toggle_float`, or `Ctrl+Alt+f` to detach the focused tiled pane
above the tree. Drag the slim top handle to move it and the bottom-right grip to resize it. Terminal
content keeps its normal touch behavior; long-press and drag inside it still reports a mouse drag to
the running program. Toggle the action again to dock the pane back into the tiled tree. The last
tiled pane in a window cannot float.

Floating positions and sizes survive Activity recreation. Workspace save and load also record each
floating pane and its bounds.

With split panes enabled, the top terminal status surface contains a horizontal window strip. Tap a
window chip to switch directly, or tap its `+` button to create a window. Labels prefer an editor's
open-file basename, then the foreground process, then the working-directory/title fallback. Tap the
session indicator to open or close the sessions drawer. The strip is hidden in single-pane
compatibility mode.

The following layouts act on the current window without restarting any shell:

| Layout | Result |
|---|---|
| `stack` | Maximize the focused pane while keeping the other panes alive and hidden |
| `grid` | Arrange panes in near-square, equally divided rows |
| `dwindle` | Tile like Hyprland: each new pane halves the focused pane along its longer side, and a pane dragged onto another takes the half it is dropped on |
| `tall` | Put a half-width master pane on the left and stack the rest on the right |
| `fat` | Put a half-height master pane on top and arrange the rest below |
| `horizontal` | Put every pane side by side at equal width |
| `vertical` | Put every pane in one top-to-bottom column at equal height |

`Ctrl+Alt+l`, **Next pane layout** in the palette, and `pane.next_layout` all cycle the window
through `grid`, `dwindle`, `tall`, `fat`, `horizontal`, `vertical`, and `stack`, in that order. A window
with no layout applied yet jumps to `grid`, so a single press never hides panes behind `stack`.

`dwindle` is the one layout that grows rather than rearranges: it never rebuilds the tree, so the
dividers you drag stay where you put them, a closed pane simply hands its space back to its
neighbour, and dragging a pane's move handle onto another pane shows which half it will take before
you let go. The first split of a portrait window stacks; a pane wider than it is tall splits side by
side.

The palette also exposes **Equalize panes** and clockwise **Rotate panes**. Choosing a specific
layout by name and moving the focused pane to an edge remain available through custom key bindings.

### The chosen layout keeps managing the window

Applying a layout retains it for that window. Splitting a pane or closing one re-tiles the survivors
into the same layout instead of leaving the split that the change happened to produce. The retained
layout survives the app recreating its Activity.

Hand-shaping the window drops the retained layout and returns it to manual control, because keeping
it would mean your next split silently discarded that shaping. Rotating, moving a pane to an edge,
and dragging or key-resizing a divider all release it. Equalizing does not, since resetting ratios is
consistent with the layout still being in charge. Applying any layout again, including through
`Ctrl+Alt+l`, puts the window back under management.

Layout changes preserve pane order and focus. `stack` is temporary: saving a workspace stores the
underlying pane tree, not the maximized presentation. Workspace *files* do not yet record the
retained layout, so a window restored from `workspace.load` starts out manually managed.

## Search and manage sessions

Open the command palette and choose **Session browser**. The browser shows the complete
session → window → pane hierarchy and can search:

- session names;
- every pane's current working directory; and
- cached foreground-process, open-file, or terminal-title labels.

Working directories inside the Termux home display as `~` or `~/subdirectory`. Directories outside
the home keep their full `/data/data/com.termux/...` path. The sessions panel uses the same display.

The session-switch chip numbers the launcher's sessions, not the number of shell processes behind
their panes and windows. It appears only when the active launcher session changes. Creating a pane
or window in the current session does not show it.

The buttons at the top create a session, clone the current session, or save the whole terminal as a
workspace. Each session row's overflow menu can activate, clone with CWD, rename, or close it. Rows
in the pop-down sessions panel carry explicit rename and close buttons; a long press still renames.

The palette lists a **Rename** row per session as well, so renaming a session other than the current
one needs neither the browser nor the panel. Its tool id is `session.rename_at_index`, which takes a
zero-based index and a name — note that this renames the tmux-style session that owns the windows,
while `session.rename` renames the focused shell.

Session names are capped at 8 characters, since they are displayed in the status row's session chip.

Cloning intentionally starts a fresh shell at the selected pane's CWD. It does not copy the running
process, shell environment changes, scrollback, windows, or pane layout. Closing a session terminates
all shells it owns; closing the last session creates a fresh shell so the app is never left without
a terminal.

## Seeing which shell is working

A window whose foreground process group is consuming CPU shows a breathing rim, and the status row
shows three pulsing dots while any window of the current session is working. The sampler sums the
whole foreground process group, so wrapper scripts are counted with their children. A shell merely
echoing input, a clock repainting occasionally, `sleep 300`, or an idle editor does not read as busy.

## Save and restore workspaces

A workspace records the ordered sessions, selected windows, recursive pane trees and ratios,
floating panes and bounds, focused panes, CWDs, and titles. Definitions are stored with owner-only
permissions at:

```text
~/.termux/workspaces/<name>.json
```

The Session browser's **Save** button or **Save workspace** in the command palette is the easiest safe
path. The save dialog records topology and CWDs by default. **Also save what is running** additionally
records the foreground command in each non-idle pane; existing names require a separate replace
confirmation.

When a saved workspace carries commands, the load flow separately asks whether to run them. Each
approved command starts again from the beginning in the normal Termux login shell; it is not a
process checkpoint. A failed or completed command leaves a usable shell behind. The picker also has
a per-row delete action; deleting a definition does not affect running sessions.

A workspace recreates terminal structure after app or service process death; it cannot resurrect
Unix processes. Loading normally starts a login shell in each recorded CWD. Advanced API callers
may save best-effort foreground argv with `captureCommands: true`, but commands are still not run
unless a later load separately supplies `runCommands: true`. Review user-edited workspace files
before enabling that option.

Available API actions are:

| Action | Arguments | Behavior |
|---|---|---|
| `workspace.save` | `name`, optional `overwrite`, optional `captureCommands` | Save the complete live hierarchy |
| `workspace.load` | `name`, optional `mode` (`append` or `replace`), optional `runCommands` | Restore a definition; defaults to safe append and no command execution |
| `workspace.list` | none | List saved definitions |
| `workspace.delete` | `name` | Delete a definition after confirmation |

Names are at most 64 Unicode code points, start with a letter or digit, and may then contain letters,
digits, spaces, `_`, `-`, or `.`. Omit the `.json` suffix.

`replace` validates and creates the replacement terminals before removing the old hierarchy. It is
still destructive and should only be used after saving anything important.

## Terminal actions and the palette

Terminal commands share the internal action registry used by the command palette, the action sheet,
and key bindings. General remote action execution over HTTP was removed together with the agent
endpoints; the local API serves TAI, the separate `/v1/apps/launch` route, and the pane routes
(`/v1/panes`, `launcherctl pane …`) through which a process in a shell can open a pane of its own,
type into it, read it back and close it — but only panes it opened itself; see
[LauncherCtl API](LauncherCtl_API.md#panes). Workspace tools (`workspace.save`, `workspace.load`,
`workspace.list`, `workspace.delete`) and the parameterized pane actions (`pane.layout`,
`pane.move_to_edge`) remain reachable from custom key bindings.

## Configuration files that ship with the app

The launcher installs its own configuration examples, so nothing has to be written from scratch:

| Path | Seeded | Purpose |
|---|---|---|
| `~/.termux/termux-launcher-bindings.conf` | On install, only when absent | Bindings, chords, modal keymaps, launching apps from a chord |
| `~/.termux/fonts.conf` | On install, only when absent | Faces, symbol maps, shaping, features, axes, cell metrics |
| `~/.termux/termux.properties` | On install, only when absent | `TERM`, volume and back keys, extra keys, cursor, scrollback, margins, colours, app behaviour |
| `~/.termux/keyboard/layout.xml` | Never — copy it yourself | In-app keyboard layout and space-bar swipe slots |
| `~/.termux/launcher/examples/` | Refreshed at every app start | Pristine copies of all of the above, plus a `README.md` |

The three seeded files arrive with every directive commented out, so a fresh install behaves exactly
as it did before they existed — uncomment what you want. They are written only when missing, so app
updates never overwrite your edits. To start over, copy the file back from
`~/.termux/launcher/examples/`.

Only one properties file is ever read: `~/.termux/termux.properties` wins, and
`~/.config/termux/termux.properties` applies only when the first is absent. A file already at that
second path is therefore left in charge — the app seeds nothing rather than shadowing it.

The keyboard layout is not seeded, because the moment `~/.termux/keyboard/layout.xml` exists it
replaces the bundled layout. Opt in explicitly:

```sh
mkdir -p ~/.termux/keyboard
cp ~/.termux/launcher/examples/keyboard-layout.xml ~/.termux/keyboard/layout.xml
termux-reload-settings
```

Nothing under `~/.termux/launcher/examples/` is read as configuration; edit the live files instead.
Files you add there yourself are left alone, but one named like a shipped example is replaced.

## Customize keyboard bindings

Create `~/.termux/termux-launcher-bindings.conf` to overlay the built-in bindings, then run:

```sh
termux-reload-settings
```

Mentioning a root sequence with `map` or `unmap` replaces every built-in mapping for that exact
sequence. Modifier names and multi-character key names are case-insensitive, but an upper-case
letter is itself Shift — `Ctrl+Alt+R` is the stroke `ctrl+alt+shift+r` and `Ctrl+Alt+r` is
`ctrl+alt+r`, two bindings that can hold two actions. `>` separates strokes in a chord.

Whatever this file binds is what the app shows: the command palette's shortcut column, the keybind
hint legend, and the caps that light up on the in-app keyboard while `Ctrl+Alt` is latched all read
the same resolved bindings, each coloured by its action's group (panes, windows, session,
workspace, terminal, clipboard, appearance, app).

```text
# Run registry actions.
map ctrl+alt+g pane.equalize
map --when splits-on ctrl+alt+e pane.equalize
unmap ctrl+alt+u

# A two-stroke chord.
map ctrl+alt+space>g pane.rotate

# Send literal input to the focused shell.
map ctrl+alt+t send-text "echo hello from a binding\n"
map ctrl+alt+enter send-key ctrl+c

# Repeating the same mapping creates an ordered multi-action binding.
map ctrl+alt+j send-text "cd ~/src\n"
map ctrl+alt+j send-text "git status\n"
```

`--when` accepts `always`, `splits-on`, or `splits-off`.

### tmux-style prefix

`leader <stroke>` declares a prefix key. Every root `ctrl+alt+…` binding then also answers to the
prefix followed by the same key, which is what makes the bindings reachable on a keyboard where
holding three keys at once is awkward:

```text
leader ctrl+space
```

With that line, `Ctrl+Space` then `m` opens the action sheet, `Ctrl+Space` then `Shift+P` opens the
command palette, and the Ctrl+Alt strokes keep working unchanged. The prefix behaves like any other
chord: the pending stroke shows in the chord overlay, the keybind hint legend lists what the next
key can be, and an unknown key or the chord timeout cancels it. A sequence the file spells out
itself is never overwritten by the generated alias, and only the first `leader` line is used.

`--label "Display name"` names a binding for the keybind hint legend, up to 32 characters. Without
one the legend prints the action's own title, which reads well for the specific actions and says
nothing useful for the generic ones: every app chord runs `app.launch`, so an unlabelled row reads
"Launch app" whichever app it starts. In a multi-line binding the first `--label` names the whole
thing.

```text
map --label WhatsApp ctrl+alt+w app.launch com.whatsapp
map --label "Repo status" ctrl+alt+j send-text "cd ~/src\n"
```

### Action arguments

Words after the action id are its arguments. Positional words fill the action's required arguments in
schema order; `name=value` reaches any argument, required or not. Values are validated against the
action's schema, so an out-of-range number or an unknown enum value is reported instead of silently
running.

```text
# Positional required arguments.
map ctrl+alt+shift+1 pane.layout grid
map ctrl+alt+shift+2 pane.move_to_edge left
map ctrl+alt+shift+w window.select 0

# Named arguments, for optional ones or for clarity.
map ctrl+alt+shift+n session.new name=build failsafe=false
map ctrl+alt+shift+s workspace.save name=project overwrite=true

# Quote a value that contains spaces.
map ctrl+alt+shift+r session.rename "build shell"
```

Arguments a stroke implies on its own — a direction for the arrow binds, a zero-based index for the
digit binds — still apply, and an argument written in the file wins over the implied one.

### Launch apps from a binding

`app.launch` takes a package name, an app label, or a stable id. An exact package match wins;
otherwise the launcher's fuzzy app ranking picks the best match, the same ranking the suggestion bar
uses.

```text
map --label WhatsApp ctrl+alt+w app.launch com.whatsapp
map --label Maps ctrl+alt+shift+m app.launch Maps
```

`--label` is what the keybind hint legend prints for the chord; without it every app row in the
legend reads "Launch app".

Installed apps also appear in the command palette under **Apps**: the most-used ones with no query,
and the full ranked match list while filtering. Selecting a row runs `app.launch`.

An app row shows the chord already bound to it, so the palette doubles as the list of what is bound —
and because the shortcut column is searchable, typing `ctrl+alt+w` finds the row it launches.

You do not have to edit the file to bind one. **Long-press an app row** (or press `Ctrl+Alt+Enter` on
the focused one) and the palette waits for a key combination: `⏎` saves, `⌫` clears, `Esc` cancels.
The binding is written to `~/.termux/termux-launcher-bindings.conf` under a managed header, labelled
with the app name the row showed, with your comments, blank lines and ordering preserved, and takes
effect immediately.

Three details are worth knowing:

- A bare key is refused. Binding plain `w` would swallow typing that character, so the overlay
  requires Ctrl, Alt, or Shift.
- `⏎`, `Esc` and `⌫` are the overlay's own keys and cannot be captured. Bind those in the file.
- If the combination is already bound, the overlay names what holds it and saves anyway — mentioning
  a sequence replaces the defaults for it, which is what the file has always meant.

### Actions on in-app keyboard keys

Keyboard swipes are not strokes and are not bound here. They live in the keyboard layout file, where
any key slot can carry a launcher action written `tool:<registry id>` — optionally
`tool:<registry id>:<glyph>` to choose what the slot draws:

```xml
<key width="4.4" role="space_bar" key0="space"
     key7="tool:app.command_palette:󱎱"
     key1="tool:window.previous:󰜳" key2="tool:window.next:󰜶"
     key3="tool:session.previous:󰜹" key4="tool:session.next:󰜰"
     key5="cursor_left" key6="cursor_right" key8="switch_backward"/>
```

Slots are `key1` NW, `key2` NE, `key3` SW, `key4` SE, `key5` W, `key6` E, `key7` N, `key8` S — the
keyboard's own eight swipe directions, unchanged. A `tool:` key reaches the same dispatcher as a
keybind and a palette row, so every tool in the registry is available on every slot with no
per-tool code and no separate binding syntax.

The shipped defaults are in `inapp-keyboard/src/main/res/xml/bottom_row.xml`; the north swipe takes
over the keyboard's layout-switch gesture and `switch_forward` is dropped, while plain east/west stay
the cursor sliders. Override the whole row by writing your own space bar key into
`~/.termux/keyboard/layout.xml` — that file is the single place swipe actions are configured.

### Hot-swap keyboard layouts

The app ships every Unexpected-Keyboard layout — ninety-one of them, from QWERTY and Dvorak to
Arabic PC and Dubeolsik. Settings ▸ Keyboard ▸ Layouts picks which ones the keyboard cycles
through and in what order; the top of that screen is the cycle, the searchable list below it is
the catalogue. The first entry, **Launcher layout**, is your own `~/.termux/keyboard/layout.xml`
when that file exists and the bundled QWERTY when it does not, so a custom layout is one member
of the ring like any other.

Nothing is bound to cycling by default — a ring of one layout has nothing to cycle — so pick how
to reach it:

```text
map ctrl+alt+l keyboard.cycle_layout                    # next layout
map ctrl+alt+shift+l keyboard.cycle_layout direction=backward   # previous
map --label Dvorak ctrl+alt+d keyboard.select_layout latn_dvorak
```

On a keyboard key, the same actions are `tool:keyboard.cycle_layout` in any slot, and the
keyboard's own `switch_forward` / `switch_backward` keys — the space bar's south swipe in the
shipped bottom row — now step the ring too. In the palette, the Keyboard section lists the ring
by name once it holds more than one layout, so a layout can be reached by typing its name. Each
swap says where it landed, and the numeric and Greek/math pads are unchanged: they are not in the
ring, they keep their own keys on the Ctrl cap, and the text key returns to whichever layout the
ring is on.

### Modal keymaps

A root key can enter a named mode. The mode can time out, decide what an unknown key does, and stay
active or end after a matched action:

```text
map --new-mode nav --timeout 10 --on-unknown passthrough --on-action keep ctrl+alt+space
map --mode nav h window.previous
map --mode nav l window.next
map --mode nav v pane.split_vertical
map --mode nav q pop-mode
map --mode nav escape pop-mode
```

Valid unknown-key policies are `beep`, `ignore`, `end`, and `passthrough`; `--on-action` accepts
`keep` or `end`. A non-focusable overlay shows pending chords and the active mode. Modes may be
stacked, and `pop-mode` exits the top one.

Invalid lines are skipped while valid lines remain active. The app logs errors and shows a bounded
toast summary. The file is limited to 256 KiB, 4,096 lines, and 4,096 characters per line.

## Fonts, symbols, shaping, and metrics

There are three ways to set the terminal font, listed here by how much work they are, least first:

1. **The in-app picker** — **Settings → Appearance → Terminal fonts**. Downloads a font family and
   writes `~/.termux/fonts.d/10-launcher.conf` for you.
2. **`~/.termux/font.ttf`** plus the optional `~/.termux/font-italic.ttf`, which is also what
   Termux:Styling writes.
3. **`~/.termux/fonts.conf`**, hand-written, for everything the picker does not expose.

**Precedence: your own `fonts.conf` beats the `fonts.d` drop-ins, which beat
`font.ttf`/Termux:Styling.** A tree with no font configuration at all behaves exactly as before: the
regular face comes from `~/.termux/font.ttf`, the optional italic from `~/.termux/font-italic.ttf`,
and Android monospace is the final fallback. Termux:Styling and manual `font.ttf` replacement
therefore continue to work untouched, and so does a `fonts.conf` written before the picker existed.

### The in-app font picker

**Settings → Appearance → Terminal fonts** installs a complete multi-face font without a shell.

- **Families** is the install path, and the only one. A curated family list — Maple Mono, Hack,
  JetBrains Mono, Fira Code, Victor Mono, Cascadia Code and more — each with its download
  size, face count, and full license text shown before anything is fetched. A star on its list row
  marks the suggested family, Maple Mono (the variable pair, so weight is an axis rather than a
  file). Installing from the list applies that family's own defaults: the app's bundled Symbols Nerd
  Font Mono for icons, the family's ligature policy, and its font features and axis values.
  Downloads are SHA-256 verified, and a metered connection is confirmed separately.
- **Tuning** carries the three toggles that reshape the managed config in place: Nerd Font icons,
  ligature policy, and the `wght` axis for a variable family.
- **Use font.ttf / Termux:Styling** deletes exactly one file, `~/.termux/fonts.d/10-launcher.conf`.
  The installed font files stay on disk and `~/.termux/fonts.conf` is never touched.

Installed layout:

```text
~/.termux/fonts/<family-id>/regular.ttf, bold.ttf, italic.ttf, bold-italic.ttf, LICENSE.txt
~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf   # extracted from the APK on first use
~/.termux/fonts.d/10-launcher.conf                # the managed config
```

That is the whole list. **The picker never touches `~/.termux/font.ttf` or
`~/.termux/font-italic.ttf`** — it creates, overwrites and deletes nothing outside the three paths
above. Those two files are yours (and Termux:Styling's), and the only thing that ever looks at them
is the loader's legacy fallback, used when no `font_family` is configured. An earlier version did
mirror the installed regular face into `font.ttf`, which silently replaced a hand-built Nerd Font and
took every icon glyph on every surface down with it; that mirroring is gone.

The practical consequence: installing a family from the picker changes the **terminal** and nothing
else. `~/.termux/font.ttf` keeps whatever you put there, and surfaces that read `font.ttf` directly
are unaffected by a pick made here.

One surprise worth expecting: a family's line metrics decide the cell height, so they decide how many
rows fit. Switching to a face with taller metrics reduces the row count, which can reflow or truncate
a full-screen TUI that is already running until it resizes itself. `modify_font cell_height` in your
own `fonts.conf` is the knob if you want a row count back.

The managed config contains only ordinary directives — face paths, the two private-use `symbol_map`
ranges when icons are on, `disable_ligatures`, and per-face `font_features`/`font_variations` — so it
can be read, copied into your own `fonts.conf`, and then removed. Every change made in the picker
rewrites it completely.

The palette, keybindings and agents reach the same code through the `fonts.pick` and `fonts.install`
tools.

### Writing `fonts.conf` by hand

```text
font_family path=~/.termux/font.ttf
bold_font path=~/.termux/font-bold.ttf
italic_font path=~/.termux/font-italic.ttf
bold_italic_font path=~/.termux/font-bold-italic.ttf

# Android system-family lookup is optional and best-effort.
# bold_font family="Roboto Mono"

# Use a Nerd Font only for selected Unicode ranges.
symbol_map U+E000-U+F8FF path=~/.termux/fonts/SymbolsNerdFontMono.ttf
symbol_map U+E0A0-U+E0D7,U+F0001 family="Symbols Nerd Font Mono"

# A named map can be targeted by font_features and font_variations.
symbol_map name=nerd U+F0000-U+FFFFD path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf

# Ordered fallback chain for code points no configured face covers.
fallback_font path=~/.termux/fonts/NotoSansMono-Regular.ttf
fallback_font family="Noto Sans Symbols 2"

disable_ligatures cursor
font_features regular +zero -liga cv01=2
font_features symbols +ss01
font_variations regular wght=425 wdth=92.5

box_drawing synthesize
box_drawing_scale 0.001,1,1.5,2
powerline_symbols font

modify_font cell_width 90%
modify_font cell_height 2px
modify_font baseline 1px
modify_font underline_position 1px
modify_font underline_thickness 150%
modify_font strikethrough_position -1px
modify_font strikethrough_thickness 125%
```

Apply changes without restarting the app:

```sh
termux-reload-settings
```

Paths are the reliable Android use case; family names only work when Android can resolve the family.
Later duplicate directives replace earlier values. `~/` expansion, quoted values, and `#` comments
are supported. Each file is capped at 64 KiB, 512 lines, and 4,096 characters per line.

### Drop-in fragments: `~/.termux/fonts.d`

`~/.termux/fonts.d/*.conf` files are loaded automatically, in ascending filename order, **before**
`~/.termux/fonts.conf`. Because a later duplicate directive replaces an earlier one, your own
`fonts.conf` always overrides an app-managed or third-party drop-in — that is the whole point of the
ordering. Any single directive you do not restate in `fonts.conf` keeps the drop-in's value, so you
can override one face and leave the rest of a fragment alone.

- Only top-level `*.conf` entries are read, and only regular readable files. Subdirectories are
  ignored, and an entry whose real parent directory is not `fonts.d` — a symlink pointing outside it
  — is skipped without an error.
- At most 32 drop-in files are read, with a 256 KiB aggregate budget across all of them. That budget
  applies to the drop-ins only: `~/.termux/fonts.conf` is always read under its own 64 KiB
  allowance and can never be squeezed out by fragments. When the next fragment would exceed the
  aggregate budget, it and every remaining fragment are skipped with one bounded error.
- Each fragment is separately subject to the 64 KiB, 512-line and 4,096-character limits.
- Errors from a fragment are prefixed `fonts.d/<file>: ` so the reported line number is unambiguous.
  Messages from `fonts.conf` are unchanged.
- A `fonts.d` fragment on its own counts as active font configuration. Faces it leaves unset still
  fall back to `font.ttf` and Android monospace exactly as with no config at all.

`10-launcher.conf` is the app's own fragment. The `10-` prefix leaves room for `05-` fragments that
the app-managed one overrides and `20-` fragments that override it.

### Font faces and symbol maps

Real bold, italic, and bold-italic faces are used when provided. Missing faces fall back to safe
synthetic styling without preventing the terminal from opening.

There are exactly four SGR faces — regular, bold, italic and bold-italic — because ANSI SGR only
distinguishes bold × italic. No escape sequence exists that asks for a fifth face, so this is a
protocol limit, not a configuration limit. It does not cap how many font **files** are active:
`symbol_map` is repeatable up to 256 directives (1,024 ranges and 64 distinct font files in total)
and `fallback_font` adds up to 8 more, so a dozen fonts can be live in one terminal at once.

`symbol_map` accepts comma-separated `U+CODEPOINT` or inclusive `U+START-U+END` ranges followed by
one `path=` or `family=` source. Later overlapping maps win. A map selects one font for the complete
grapheme cluster beginning in that range; unmapped text continues through the primary face, the
fallback chain, and then Android's normal fallback. Symbol fonts do not change cell width and do not
receive SGR bold/italic synthesis.

A map may be named, so features and axes can address it:

```text
symbol_map name=nerd U+E000-U+F8FF path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf
symbol_map U+2500-U+257F name=frames path=~/.termux/fonts/BoxFrames.ttf
font_features nerd +ss01
font_variations nerd wght=600
```

- `name=` may appear on either side of the ranges, but only once per line.
- A name is 1 to 32 characters of `A-Z a-z 0-9 _ -`, and is matched case-insensitively.
- The reserved targets `regular`, `bold`, `italic`, `bold_italic` and `symbols` are rejected as map
  names.
- A name may be declared in a file loaded *after* the `font_features`/`font_variations` line that
  references it, so a drop-in and your `fonts.conf` can be written in either order. A name that is
  never declared anywhere in the load is reported and its setting is dropped.
- At most 256 named targets carry settings.
- Unnamed maps keep using the shared `symbols` target exactly as before.

A mapped cell draws with its own map's features and axes when that map declares them, and falls back
to the shared `symbols` target when it does not. So `font_features symbols` remains the way to set
one policy for every symbol font, and a named target overrides it for that map alone. Two maps may
name the same font file and still declare different settings; each map's cells are drawn with its own.

An axis Android rejects is reported and dropped per map, leaving that map's font at its default
instance rather than disabling the map — under the map's name when the axes were its own, or under
`symbols` when they were inherited, so the message names the line you actually wrote.

### Fallback fonts

`fallback_font` is the controllable answer to "Android picked a CJK or emoji font I did not choose".
It is repeatable, takes one `path=` or `family=` source, and is capped at 8 entries; order of
appearance is the try order.

```text
fallback_font path=~/.termux/fonts/NotoSansMonoCJK-Regular.otf
fallback_font family="Noto Sans Symbols 2"
```

The effective per-cell precedence is:

1. an explicit `symbol_map` range,
2. box-drawing synthesis,
3. the configured face for the cell's SGR style,
4. the `fallback_font` chain, in order, and
5. Android's own platform fallback.

The chain is consulted only when the run's own face genuinely lacks the glyph. Coverage is probed
with `Paint.hasGlyph` on the cluster's base code point — never on a continuation, which is the same
rule `symbol_map` follows — and every answer is memoized per code point and SGR face, so the probe
runs at most once each. A chain entry is one face with no declared variants, so any bold or italic
the primary face would have shown is synthesized on top of it rather than dropped. A fallback face
never changes cell width.

### Geometric box drawing, blocks, braille, and Powerline

Box-drawing, block, shade, braille and sextant cells are drawn as geometry snapped to shared integer
cell edges rather than shaped from the font's own glyphs. Both cells that meet at a boundary derive
that boundary from the same expression, so frames, block ramps and braille graphs join seamlessly at
any cell size — including after `modify_font cell_width`/`cell_height` — instead of showing the
hairline gaps a font's glyph metrics produce at fractional cell sizes.

```text
box_drawing synthesize            # or: font
box_drawing_scale 0.001,1,1.5,2   # thin, light, heavy, very heavy
powerline_symbols font            # or: synthesize
```

- `box_drawing` defaults to `synthesize`. `box_drawing font` restores glyph rendering for every one
  of these code points exactly as before.
- `box_drawing_scale` takes exactly four comma- or space-separated stroke multipliers for the thin,
  light, heavy and very-heavy line weights. Each must be greater than 0 and at most 8. The default
  is `0.001,1,1.5,2`; the base stroke is one sixteenth of the cell height, and every weight is
  clamped to at least one pixel and at most a third of the cell's smaller dimension.
- `powerline_symbols` defaults to `font`, because a patched Nerd Font usually draws the separators
  the way its author intended. `synthesize` claims them instead, so two consecutive separators butt
  against each other with no sliver of background between them. It only takes effect while
  `box_drawing` is also `synthesize`.
- An explicit `symbol_map` covering a code point always wins over synthesis: asking for that font
  was a deliberate choice.
- Shades render as the foreground colour at reduced alpha — 25% for `░`, 50% for `▒`, 75% for `▓` —
  so they follow SGR colour, dim, selection and block-cursor inversion like any other cell.

Synthesized ranges:

| Range | Contents |
|---|---|
| `U+2500-U+257F` | box drawing: lines, dashes, corners, tees, crosses, arcs, diagonals |
| `U+2580-U+259F` | block elements, eighth blocks, quadrants, and the three shades |
| `U+25E2-U+25E5` | corner triangles |
| `U+2800-U+28FF` | the complete braille pattern block |
| `U+1FB00-U+1FB3B` | legacy-computing sextants |
| `U+1FB70-U+1FB8F` | legacy-computing eighth blocks and half shades |
| `U+E0B0-U+E0B7`, `U+E0BA-U+E0BD` | Powerline separators, only with `powerline_symbols synthesize` |

Deliberately **not** synthesized — these still come from the font, and a font that lacks them still
shows tofu:

| Range | Contents |
|---|---|
| `U+1FB3C-U+1FB6F` | wedges and diagonal fills |
| `U+1FB90-U+1FBFF` | inverse shades, pattern fills, and segmented digits |
| `U+1CD00-U+1CDE5` | octants (Symbols for Legacy Computing Supplement) |
| `U+E0B8-U+E0B9`, `U+E0BE-U+E0BF` | the remaining Powerline separator variants |

### Ligatures, features, and variable axes

`disable_ligatures` accepts:

- `never`: keep programming ligatures everywhere; this is the default;
- `cursor`: disable the contextual-ligature feature only at the cursor; or
- `always`: disable it for every rendered run.

This policy controls programming ligatures without disabling Arabic, Indic, emoji, or combining-mark
shaping. Text remains in terminal logical order; Unicode bidi paragraph reordering is not provided.

`font_features` and `font_variations` target `regular`, `bold`, `italic`, `bold_italic`, `symbols`,
or the name of a declared `symbol_map`. Feature tokens use `+tag`, `-tag`, or `tag=value`; axes use
`tag=value`. Commas and spaces are both accepted, and `none` clears the target's previous setting. A
target carries at most 32 features and 16 axes. A static font may accept a variation axis as a no-op,
so use an actual variable font when axis changes matter.

### Cell and decoration metrics

`modify_font` supports `cell_width`, `cell_height`, `baseline`, `underline_position`,
`underline_thickness`, `strikethrough_position`, and `strikethrough_thickness`.

- A percentage replaces the font-derived metric.
- A value ending in `px`, or a bare number, adds pixels. Bare values are pixels on Android, not
  Kitty desktop point units.
- `none` clears a previous adjustment.
- A positive baseline raises glyphs and decorations; a positive decoration position moves that
  decoration downward.

Font files are untrusted input parsed by Android. Files larger than 64 MiB, unreadable files,
malformed fonts, excessive mappings, and invalid settings are rejected with a bounded error and a
safe fallback. Keep font files owner-writable only and obtain them from a source you trust.

### Nerd Font icons on app chrome, not just in the terminal

This is a difference from Termux worth stating plainly: **in Termux, a Nerd Font icon only renders
where your terminal font renders it.** Everything outside the terminal grid — the notification, the
extra-keys row, a session name in a drawer — draws with an Android system font, and no Android
system font covers the Private Use Areas that Nerd Fonts populate. An icon in a session name is
therefore tofu on every surface but the grid.

Termux Launcher bundles the symbols-only face (`assets/fonts/SymbolsNerdFontMono.ttf`) and spans it
onto the app's own chrome, so one icon looks the same everywhere it appears:

- the status bar and its session/window indicators,
- the window bar's tab labels,
- the extra-keys row and its swipe-up badges,
- the in-app keyboard's key caps,
- the extra-keys editor and its glyph picker.

The mechanism is `NerdFontSpans`. It walks a label **by code point**, not by `char` — the Material
Design ranges are astral, so a `char` walk sees two unmapped surrogates instead of one icon — and
wraps each run of PUA code points (`U+E000-U+F8FF` and `U+F0000-U+FFFFD`) in a typeface span
pointing at the bundled face. Everything else keeps the label's own font, and a label with no icons
is returned unchanged, allocating nothing. A styled label keeps its style: the span synthesizes bold
or italic when the symbols face has no such cut, which is why an icon in a selected, bold tab still
looks bold.

The terminal grid is unaffected by all of this. It keeps its own font stack and its `symbol_map`
routing (see [Route symbols deliberately](Terminal_Fonts.md#route-symbols-deliberately)). Where both
apply — the window bar draws terminal-derived names — the bundled face is applied first and any
`symbol_map` face second, so a face you configured wins and the bundled one only fills runs your
configuration left uncovered.

**Editing key labels.** Because the same face backs the key caps, any Nerd Font icon works as a key
label. Paste one into **display** in the extra-keys editor, or into `extra-keys` in
`~/.termux/termux.properties` — that file is read as UTF-8, so paste the character itself; `\uXXXX`
escapes are not interpreted and could not reach the astral ranges anyway. The editor re-spans the
field as you type, so an icon is drawn rather than boxed while you are still editing it.

**Finding an icon.** The glyph picker (**Settings → Terminal & status → Edit extra keys**, then a
key's glyph field) ships the whole bundled set: **10,512 icons**, searchable by name (`keyboard`),
by family (`md`, `fa`, `oct`, `cod`, `dev`, `weather`), or by exact Nerd Font name
(`nf-md-folder`). Browsing shows a shelf of the set with the rest behind search, because a grid of
ten thousand live cells is not a grid anyone scrolls. That catalogue is generated from the shipped
font's own name table by `scripts/generate-nerd-font-glyph-catalogue.py`, so it can never offer a
glyph the app cannot draw; regenerate it whenever the bundled font is updated. Reviewed non-icon
glyphs — arrows, blocks, box drawing, Powerline — are a separate, hand-curated list, and those
**are** filtered per device against the UI font, since nothing bundled backs them.

The space bar's swipe slots and the default extra-keys row both use these icons out of the box; see
[Actions on in-app keyboard keys](#actions-on-in-app-keyboard-keys).

## Scrollback, links, and shell navigation

### Hints and search

Press `Ctrl+Alt+u` for keyboard-labelled hints extracted from visible transcript content. Hints
recognize URLs, absolute and relative paths, hashes, and `path:line[:column]` locations. Press a
label to open a URL or copy another hint; hold Shift while choosing a URL to copy it instead.

Press `Ctrl+Alt+s` to search the focused terminal's history and screen case-insensitively, then
choose a result to scroll directly to its emulator row.

### Safe hyperlinks

Applications may emit OSC 8 hyperlinks. Linked cells are visibly underlined and tapping one shows
the full target before anything opens. Only `http`, `https`, `mailto`, `tel`, `sms`, `geo`, `ftp`,
and `ftps` can be opened. Other schemes, including `file`, can only be copied.

The implementation bounds URI length and link-pool size. If the pool is exhausted, new links degrade
to ordinary text instead of consuming unbounded memory.

### Jump between shell prompts

The palette actions **Jump to previous prompt** and **Jump to next prompt** use OSC 133 shell marks.
fish 4 emits compatible marks without setup. Bash and zsh users can enable the app-managed scripts:

```sh
# Add this to ~/.bashrc:
source ~/.termux/shell-integration/termux-launcher.bash

# Or add this to ~/.zshrc:
source ~/.termux/shell-integration/termux-launcher.zsh
```

The app installs and updates those scripts but never edits shell rc files. The integrations preserve
existing Bash `PROMPT_COMMAND` and zsh `precmd`/`preexec` hooks and are safe to source more than once.
Open a new shell, or source the relevant rc file, after enabling them.

## Rendering and application compatibility

The terminal supports these application-facing capabilities without user configuration:

- underline styles: single, double, curly, dotted, and dashed, including independent SGR 58 color;
- grapheme-aware fixed-cell shaping for combining marks, Indic conjuncts, Arabic shaping in logical
  order, ZWJ emoji, regional-indicator flags, and programming ligatures;
- Kitty keyboard protocol negotiation, including disambiguation, event reporting, alternate keys,
  all-keys reporting, and associated text;
- Kitty multiple-cursors protocol, including point/rectangle cursors, shapes, and colors;
- Kitty graphics through the Tier 2 core: direct PNG and raw RGB/RGBA pixel transmission (including
  zlib-compressed raw data), chunking, stored images (`a=t`) with image ids and numbers, placements
  (`a=p`) with source rectangles, cell scaling, sub-cell offsets, and z-index, acknowledgments,
  quiet modes, and the full set of delete forms;
- Kitty graphics animation: frame transmission (`a=f`) with partial-frame rectangles, base-frame
  and background-colour composition, and per-frame gaps; animation control (`a=a`) for both
  client-driven (current-frame) and terminal-driven (gap-timed, looping) playback; frame
  composition (`a=c`); and frame deletion (`d=f`/`d=F`) — animated GIFs sent through the protocol
  keep playing on the terminal's own clock after the sending program exits; and
- existing Sixel/iTerm bitmap rendering paths.

Programs negotiate the keyboard and graphics protocols themselves. Legacy applications continue
through the normal Termux key encoder. Main and alternate screens retain independent Kitty keyboard
flags and bounded mode stacks.

Capability detectors can identify the terminal without heuristics. Every shell receives:

```sh
TERM_PROGRAM=termux-launcher
TERM_PROGRAM_VERSION=<installed version>
```

XTVERSION replies with `termux-launcher(version)`. XTSMGRAPHICS reports the Sixel color-register
count and geometry that follows the current screen, so chafa, notcurses, and similar tools can choose
their supported path.

Unicode placeholders and shared-memory or file transmission remain out of scope and
return a bounded protocol error. Images placed with a negative z-index never overwrite visible
text: the terminal keeps the text and shows the image in the surrounding blank cells.
`timg -pk` (PNG), `chafa -f kitty` (raw RGBA), and yazi image previews all work with no
configuration.

## Appearance and diagnostics

When wallpaper colors are enabled, `~/.termux/material-colors.sh` and `.properties` export the full
Material role set used by the launcher. Container colors include their matching `on_*_container`
foreground, along with tertiary, error-container, and outline roles, so a shell prompt can use a
guaranteed-contrast pair. The files are rewritten only when their content changes.

The animated cursor trail is enabled by default and automatically suppressed in Android power-save
mode. Toggle it from the command palette with **Toggle cursor trail**. Large cursor jumps and ordinary
one-cell typing are not animated.

Open **Key inspector** from the command palette when debugging a keyboard. Its non-focusable overlay
shows the Android key event, the registry binding that claimed it, active Kitty keyboard flags, and
the exact bytes written to the shell. It is intentionally unbound so it can inspect any candidate
shortcut, including the one you might later assign to it.

## Troubleshooting

### A shortcut reaches the shell instead of the app

- Check whether **Settings → Terminal & status → Split-pane controls** changes the shortcut's
  meaning.
- Open **Key inspector** and press the shortcut.
- Check `~/.termux/termux-launcher-bindings.conf` for an override or `unmap`.
- Run `termux-reload-settings` after editing configuration.

### A font setting is ignored

- Check `ls ~/.termux/fonts.d/`. A drop-in is read before `fonts.conf`, so a directive you set there
  wins — but a directive only present in a drop-in is still active. `10-launcher.conf` is written by
  the in-app picker; remove it from **Settings → Appearance → Terminal fonts → Use font.ttf /
  Termux:Styling**, not by hand-editing it, since the next picker action rewrites it.
- Use `path=` for downloaded font files; Android may not know their family name.
- Check that Termux can read the file and that it is non-empty and below 64 MiB.
- Confirm every feature or axis tag is exactly four characters, and that a `font_features` or
  `font_variations` line naming a symbol map matches a `symbol_map name=` declared somewhere in the
  load.
- Look for the app's bounded font-error toast and app log, then retry with one directive at a time.
  An error prefixed `fonts.d/<file>:` names the fragment, not `fonts.conf`.
- Remove or rename `fonts.conf` and reload to return to native `font.ttf` behavior.

### Frames or blocks look wrong, or icons come from the wrong font

- Box drawing, blocks, shades, braille and sextants are drawn as geometry by default. Set
  `box_drawing font` to hand every one of them back to the font, or map the range with an explicit
  `symbol_map`, which always wins over synthesis.
- Wedges (`U+1FB3C-U+1FB6F`), inverse shades and pattern fills (`U+1FB90-U+1FBFF`) and octants
  (`U+1CD00-U+1CDE5`) are deliberately not synthesized, so those still need a font that covers them.
- Powerline separators come from the font unless you set `powerline_symbols synthesize`, which also
  requires `box_drawing synthesize`.
- When Android substitutes a CJK or emoji face you did not choose, add `fallback_font` entries in the
  order you want them tried instead of fighting the platform fallback.

### Prompt navigation says there are no marks

Start a new shell after sourcing the Bash or zsh integration. fish must be version 4 or newer for
its built-in marks. Existing scrollback created before integration was enabled has no marks.

### A palette action is greyed out or does nothing

Greyed-out rows show the reason inline (for example split panes disabled or no current session).
Pane and window commands need split panes enabled; enable them in settings and retry.

## Current limitations

- Workspace files do not record a window's retained layout, so a restored window starts manually
  managed. Direct `goto_layout`/`toggle_layout` bindings are still missing, because user-editable
  bindings cannot carry an enum argument yet.
- The terminal deliberately has no arbitrary Kitty multicell/variable-sized text,
  `narrow_symbols`, or symbols that occupy following cells.
- Geometric cell rendering covers the ranges tabulated above. The legacy-computing wedges, inverse
  shades, pattern fills and segmented digits, and the whole octant block at `U+1CD00-U+1CDE5`, are
  still drawn from the font.
- There is no Unicode bidi paragraph layout. Complex scripts are shaped in logical terminal order.
- The renderer remains Android Canvas; there is no GPU glyph atlas or custom gamma/contrast compositor.
- Kitty graphics Tier 2/3, desktop notification escape sequences, TTY file transfer, Kitty kittens,
  and multiple Android top-level terminal windows are not implemented.

See [LauncherCtl API](LauncherCtl_API) for the authenticated TAI and app-launch routes and
[Launcher troubleshooting](Launcher_Troubleshooting) for general app diagnostics.
