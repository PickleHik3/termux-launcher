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
    └── Pane tree
        ├── Pane (one shell/PTTY)
        └── Pane (one shell/PTTY)
```

- A **session** is a drawer entry. It may contain several windows.
- A **window** is one workspace inside a session. Only the selected window is visible.
- A **pane** is one live shell inside a window. Panes can be split recursively in either direction.

Split panes are enabled by default. To restore traditional single-pane Termux behavior, open
**Settings → Termux → Terminal IO → Split panes** and enable **Single-pane compatibility mode**.
Enabling compatibility mode closes secondary panes and disables window/pane commands, so finish or
save work in those shells first.

The fastest way to discover commands is the **Command palette**:

- Long-press the terminal, choose **Command palette**, and search by name or description.
- On a hardware keyboard, press `Ctrl+Alt+Shift+P`.
- The built-in chord `Ctrl+Alt+Space`, then `P`, opens the same palette.

Unavailable actions stay visible but are disabled with a reason, for example when there is no text
selection. Actions that can destroy or disclose data ask for confirmation.

The palette is the complete argument-free UI surface:

| Category | Available operations |
|---|---|
| Pane | Split horizontally/vertically, equalize, rotate, terminate focused pane |
| Window | Create, close, next, previous, and rename |
| Session | Create, browse, clone with CWD, next, previous, close, and rename |
| Terminal | Keyboard/toolbar toggles, font size, URL picker, hints, scrollback search, prompt navigation, sharing, and reset |
| Clipboard | Paste and copy selected text |
| Appearance | Wallpaper picker/toggle, cursor-trail toggle, and Glass Lab |
| App | Settings destinations, drawer controls, command palette, action sheet, and key inspector |

The long-press **Terminal action sheet** remains deliberately short: command palette, URL picker,
share transcript, wallpaper controls, Glass Lab, settings, reset terminal, and kill process. Use the
palette for the full searchable surface.

## Default keyboard shortcuts

Shortcuts match Android key codes, so they follow physical key positions rather than the character
produced by the current keyboard layout.

| Shortcut | Split panes enabled | Compatibility mode |
|---|---|---|
| `Ctrl+Alt+V` | Split vertically (side by side) | Paste |
| `Ctrl+Alt+H` | Split horizontally (stacked) | Sent to the shell if unclaimed |
| `Ctrl+Alt+Arrow` | Focus the pane in that direction | Left/right opens or closes the session drawer; up/down changes session |
| `Ctrl+Alt+Shift+Arrow` | Resize the focused pane | Sent to the shell if unclaimed |
| `Ctrl+Alt+C` | New window | New session |
| `Ctrl+Alt+X` | Close current window, after confirmation | Sent to the shell if unclaimed |
| `Ctrl+Alt+[` / `Ctrl+Alt+]` | Previous/next window | Sent to the shell if unclaimed |
| `Ctrl+Alt+R` | Rename current window | Rename current session |
| `Ctrl+Alt+Shift+C` | New session | New session |
| `Ctrl+Alt+Shift+X` | Close current session, after confirmation | Sent to the shell if unclaimed |
| `Ctrl+Alt+N` / `Ctrl+Alt+P` | Next/previous session | Next/previous session |
| `Ctrl+Alt+1` … `Ctrl+Alt+9` | Activate that drawer session | Activate that drawer session |
| `Ctrl+Alt+K` | Toggle soft keyboard | Toggle soft keyboard |
| `Ctrl+Alt++` / `Ctrl+Alt+-` | Increase/decrease font size | Increase/decrease font size |
| `Ctrl+Alt+M` | Open terminal action sheet | Open terminal action sheet |
| `Ctrl+Alt+U` | Open terminal hints | Open terminal hints |
| `Ctrl+Alt+S` | Search scrollback | Search scrollback |

“Vertical split” means a vertical dividing line and therefore creates side-by-side panes.

## Panes, windows, and layouts

Use the default shortcuts or search the command palette for pane and window actions. Splitting starts
a fresh shell in the focused pane's working directory. Each pane retains its process, scrollback,
selection, and terminal state while you focus another pane or window.

With split panes enabled, the top terminal status surface contains a horizontal window strip. Tap a
window chip to switch directly, or tap its `+` button to create a window. Labels prefer an editor's
open-file basename, then the foreground process, then the working-directory/title fallback. Tap the
session indicator to open or close the sessions drawer. The strip is hidden in single-pane
compatibility mode.

The following one-shot layouts act on the current window without restarting any shell:

| Layout | Result |
|---|---|
| `stack` | Maximize the focused pane while keeping the other panes alive and hidden |
| `grid` | Arrange panes in near-square, equally divided rows |
| `tall` | Put a half-width master pane on the left and stack the rest on the right |
| `fat` | Put a half-height master pane on top and arrange the rest below |
| `horizontal` | Put every pane side by side at equal width |
| `vertical` | Put every pane in one top-to-bottom column at equal height |

The palette also exposes **Equalize panes** and clockwise **Rotate panes**. Layouts requiring an
argument, and moving the focused pane to an edge, are available through the authenticated action
API described in [Run terminal actions from the shell](#run-terminal-actions-from-the-shell).

Layout changes preserve pane order and focus. `stack` is temporary: saving a workspace stores the
underlying pane tree, not the maximized presentation. Layouts currently transform the tree once;
they do not automatically rearrange later splits or closes.

## Search and manage sessions

Open the command palette and choose **Session browser**. The browser shows the complete
session → window → pane hierarchy and can search:

- session names;
- every pane's current working directory; and
- cached foreground-process, open-file, or terminal-title labels.

The buttons at the top create a session, clone the current session, or save the whole terminal as a
workspace. Each session row's overflow menu can activate, clone with CWD, rename, or close it.

Cloning intentionally starts a fresh shell at the selected pane's CWD. It does not copy the running
process, shell environment changes, scrollback, windows, or pane layout. Closing a session terminates
all shells it owns; closing the last session creates a fresh shell so the app is never left without
a terminal.

## Save and restore workspaces

A workspace records the ordered sessions, selected windows, recursive pane trees and ratios,
focused panes, CWDs, and titles. Definitions are stored with owner-only permissions at:

```text
~/.termux/workspaces/<name>.json
```

The Session browser's **Save** button is the easiest safe path. It saves topology and CWDs but does
not capture foreground commands. Existing names require a separate replace confirmation.

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

## Run terminal actions from the shell

Terminal commands share the LauncherCtl registry used by the palette and agents. Open Termux
Launcher once, then inspect the live schemas:

```sh
launcherctl status
launcherctl tools
```

The current `launcherctl` wrapper does not have a generic `execute` subcommand. This helper safely
calls the authenticated local endpoint and accepts the tool arguments as JSON:

```sh
launcher_tool() {
    tool=$1
    args=${2-}
    [ -n "$args" ] || args='{}'
    base=$(sed -n '1p' ~/.launcherctl/endpoint) || return
    token=$(cat ~/.launcherctl/token) || return
    payload=$(jq -cn --arg tool "$tool" --argjson arguments "$args" \
        '{tool: $tool, arguments: $arguments, confirm: true}') || return
    curl -fsS -X POST \
        -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' \
        --data "$payload" \
        "$base/v1/agent/execute"
}
```

Install `jq` and `curl` first if needed: `pkg install jq curl`. Examples:

```sh
launcher_tool pane.layout '{"layout":"grid"}'
launcher_tool pane.move_to_edge '{"edge":"left"}'
launcher_tool pane.equalize
launcher_tool pane.rotate '{"direction":"counterclockwise"}'

launcher_tool workspace.save '{"name":"project","overwrite":true}'
launcher_tool workspace.list
launcher_tool workspace.load '{"name":"project","mode":"append"}'
launcher_tool workspace.delete '{"name":"project"}'

launcher_tool terminal.state
launcher_tool terminal.state '{"resetPerformance":true}'
```

The helper passes confirmation for every request. Use only tool names and arguments you have
reviewed; medium- and high-risk actions include paste, sharing, closing terminals, command-enabled
workspace loading, and persistent appearance changes. Treat `~/.launcherctl/token` like a password.

## Customize keyboard bindings

Create `~/.termux/termux-launcher-bindings.conf` to overlay the built-in bindings, then run:

```sh
termux-reload-settings
```

Mentioning a root sequence with `map` or `unmap` replaces every built-in mapping for that exact
sequence. Key names are case-insensitive and `>` separates strokes in a chord.

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

`--when` accepts `always`, `splits-on`, or `splits-off`. Inline arguments are not supported for
registry actions, so argument-requiring actions such as `pane.layout` cannot yet be meaningfully
bound without a dedicated argument-free action.

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
stacked, and `pop-mode` exits the top one. Prefer argument-free actions in custom maps until binding
arguments are added.

Invalid lines are skipped while valid lines remain active. The app logs errors and shows a bounded
toast summary. The file is limited to 256 KiB, 4,096 lines, and 4,096 characters per line.

## Fonts, symbols, shaping, and metrics

Native Termux font handling remains compatible. With no `fonts.conf`, the regular face comes from
`~/.termux/font.ttf`, optional italic from `~/.termux/font-italic.ttf`, and Android monospace is the
final fallback. Termux:Styling and manual `font.ttf` replacement therefore continue to work.

For independent faces and advanced controls, create `~/.termux/fonts.conf`:

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

disable_ligatures cursor
font_features regular +zero -liga cv01=2
font_features symbols +ss01
font_variations regular wght=425 wdth=92.5

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
are supported.

### Font faces and symbol maps

Real bold, italic, and bold-italic faces are used when provided. Missing faces fall back to safe
synthetic styling without preventing the terminal from opening.

`symbol_map` is repeatable and accepts comma-separated `U+CODEPOINT` or inclusive
`U+START-U+END` ranges. Later overlapping maps win. A map selects one font for the complete grapheme
cluster beginning in that range; unmapped text continues through the primary face and Android's
normal fallback. Symbol fonts do not change cell width.

### Ligatures, features, and variable axes

`disable_ligatures` accepts:

- `never`: keep programming ligatures everywhere; this is the default;
- `cursor`: disable the contextual-ligature feature only at the cursor; or
- `always`: disable it for every rendered run.

This policy controls programming ligatures without disabling Arabic, Indic, emoji, or combining-mark
shaping. Text remains in terminal logical order; Unicode bidi paragraph reordering is not provided.

`font_features` and `font_variations` target `regular`, `bold`, `italic`, `bold_italic`, or
`symbols`. Feature tokens use `+tag`, `-tag`, or `tag=value`; axes use `tag=value`. Commas and spaces
are both accepted, and `none` clears the target's previous setting. A static font may accept a
variation axis as a no-op, so use an actual variable font when axis changes matter.

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

## Scrollback, links, and shell navigation

### Hints and search

Press `Ctrl+Alt+U` for keyboard-labelled hints extracted from visible transcript content. Hints
recognize URLs, absolute and relative paths, hashes, and `path:line[:column]` locations. Press a
label to open a URL or copy another hint; hold Shift while choosing a URL to copy it instead.

Press `Ctrl+Alt+S` to search the focused terminal's history and screen case-insensitively, then
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
- Kitty graphics Tier 1: direct PNG transmission, chunking, display, placement, scaling, acknowledgments,
  quiet modes, and deletion; and
- existing Sixel/iTerm bitmap rendering paths.

Programs negotiate the keyboard and graphics protocols themselves. Legacy applications continue
through the normal Termux key encoder. Main and alternate screens retain independent Kitty keyboard
flags and bounded mode stacks.

Kitty graphics support is intentionally Tier 1. Shared memory, temporary-file transmission,
animation, composition, pixel queries, and other Tier 2/3 operations return a bounded protocol error.

## Appearance and diagnostics

The animated cursor trail is enabled by default and automatically suppressed in Android power-save
mode. Toggle it from the command palette with **Toggle cursor trail**. Large cursor jumps and ordinary
one-cell typing are not animated.

Open **Key inspector** from the command palette when debugging a keyboard. Its non-focusable overlay
shows the Android key event, the registry binding that claimed it, active Kitty keyboard flags, and
the exact bytes written to the shell. It is intentionally unbound so it can inspect any candidate
shortcut, including the one you might later assign to it.

`terminal.state` on the authenticated API reports the live terminal hierarchy and performance
counters. Counters include whole-window frame timing/deadline misses, per-pane render timing, process
allocation deltas, GC deltas, and listener-report loss. Pass `resetPerformance: true` to establish a
new common measurement baseline. These are app/render diagnostics, not SurfaceFlinger presentation
telemetry.

## Troubleshooting

### A shortcut reaches the shell instead of the app

- Check whether **Single-pane compatibility mode** changes the shortcut's meaning.
- Open **Key inspector** and press the shortcut.
- Check `~/.termux/termux-launcher-bindings.conf` for an override or `unmap`.
- Run `termux-reload-settings` after editing configuration.

### A font setting is ignored

- Use `path=` for downloaded font files; Android may not know their family name.
- Check that Termux can read the file and that it is non-empty and below 64 MiB.
- Confirm every feature or axis tag is exactly four characters.
- Look for the app's bounded font-error toast and app log, then retry with one directive at a time.
- Remove or rename `fonts.conf` and reload to return to native `font.ttf` behavior.

### Prompt navigation says there are no marks

Start a new shell after sourcing the Bash or zsh integration. fish must be version 4 or newer for
its built-in marks. Existing scrollback created before integration was enabled has no marks.

### LauncherCtl cannot execute an action

Open Termux Launcher once, run `launcherctl status`, and verify that
`~/.launcherctl/endpoint` and `~/.launcherctl/token` exist. Use `launcherctl tools` for the exact
schema shipped by the installed APK. A `409` response usually means the current state cannot perform
the action, such as using a pane command in compatibility mode.

## Current limitations

- Layout presets are one-shot. There is no retained automatic-layout policy or `next_layout` cycle
  yet.
- The terminal deliberately has no arbitrary Kitty multicell/variable-sized text,
  `narrow_symbols`, or symbols that occupy following cells.
- There is no Unicode bidi paragraph layout. Complex scripts are shaped in logical terminal order.
- The renderer remains Android Canvas; there is no GPU glyph atlas or custom gamma/contrast compositor.
- Kitty graphics Tier 2/3, desktop notification escape sequences, TTY file transfer, Kitty kittens,
  and multiple Android top-level terminal windows are not implemented.

See [LauncherCtl API](LauncherCtl_API) for the complete authenticated bridge and
[Launcher troubleshooting](Launcher_Troubleshooting) for general app diagnostics.
