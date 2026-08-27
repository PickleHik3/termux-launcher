# What’s new in 0.2.31

Version 0.2.31 focuses on making the native launcher and terminal easier to discover, more useful in
landscape, and safer to restore. This page covers user-visible changes; the complete fix list remains
in the [v0.2.31 release notes](https://github.com/PickleHik3/termux-launcher/releases/tag/v0.2.31).

## Start faster

### First-launch tour

The first open presents three skippable pages over real launcher footage. They cover the initial
package/storage commands, `%` app search and the A–Z rail, then terminal windows and live status
surfaces.

### Search all settings

**Search settings** now indexes preferences inside every destination. Queries such as `fonts`,
`ligatures`, or `Shizuku` find the containing settings section instead of only matching the seven
top-level rows.

## Use the launcher in landscape

Landscape now has its own deliberate layout:

- pinned apps form a vertical rail on the left;
- the status row, terminal, action row, and keyboard stay clear of the rail and display cutout;
- keyboard height is stored separately for each orientation; and
- the landscape height limit preserves usable terminal rows.

Portrait behavior is unchanged.

## Restore more of a workspace

**Save workspace** includes an optional **Also save what is running** checkbox. It records the
foreground command in each non-idle pane. Loading a workspace that contains commands separately asks
whether to run them, including the exact number.

This is a restart, not a checkpoint: every approved command begins again through the normal Termux
login shell. Commands are never recorded or run without separate choices. The workspace picker also
has a confirmed per-row delete action that does not affect running shells.

## Zoom one pane at a time

Pinch zoom and font-size actions now pin the size only to the focused pane. A new split inherits the
source pane's size; a new window inherits the size of the pane it came from. Unzoomed panes continue
following the global default, and the scratchpad keeps an independent size.

## Install and tune terminal fonts in the app

Open **Settings → Appearance → Terminal fonts** to choose from fourteen curated families. The picker
shows download size and license, verifies every download with a pinned SHA-256, and applies each
family's icon, ligature, and feature defaults.

It also adds:

- Nerd Font icon routing that works with every family;
- ligature policy and variable-weight controls where supported;
- ordered `~/.termux/fonts.d/*.conf` fragments before the user's `fonts.conf`;
- up to eight explicit fallback fonts;
- named symbol maps with independent features and variable axes;
- geometric box, block, braille, sextant, and default Powerline rendering without hairline gaps.

The picker owns `~/.termux/fonts.d/10-launcher.conf`. **Use font.ttf / Termux:Styling** removes only
that file and leaves user configuration and installed font files intact. See the dedicated
[terminal fonts guide](Terminal_Fonts.md).

## Bind an Android app to a key

Search for an installed app in the command palette, then long-press its row—or focus it and press
`Ctrl+Alt+Enter`. Press a modified key combination and confirm it. The palette writes the mapping to
`~/.termux/termux-launcher-bindings.conf`, preserves surrounding comments and ordering, and applies it
immediately.

App rows display and can search by their existing shortcut. Uppercase letters now explicitly mean
Shift, so `Ctrl+Alt+r` and `Ctrl+Alt+R` can run different actions.

The same resolved bindings light the built-in keyboard while `Ctrl+Alt` is latched. Pressing a lit
cap runs its binding, and colors distinguish pane, window, session, workspace, terminal, clipboard,
appearance, and app action families.

## See which window is busy—or needs you

- A window pill breathes while its foreground process group is consuming CPU. Wrapper scripts count
  with their children; typing, an idle editor, and an occasional TUI repaint do not look busy.
- A background window that rings the terminal bell gains a pulsing Material error-colored rim. This
  covers prompts, agents, and jobs waiting for input. Focusing the window clears the attention state.
- Background-command notices are grouped per session, because windows and panes in the visible
  session already expose working and waiting state in their pills.

## Manage sessions without hidden gestures

The expanded sessions panel now shows explicit rename and close buttons on every row; long-press
rename still works. The command palette also has a Rename action for each session, including sessions
that are not active. Session names may contain up to eight characters.

The expanded status panel has a settings cog. Tapping its clock opens the Android clock app.

## Tune contrast and use more Material colors

The terminal tab in **Surface editor** adds **Softer**, **Default**, and **Harder** contrast. It applies
live when wallpaper colors are enabled.

The generated `~/.termux/material-colors.sh` and `.properties` files now include all Material
container/on-container pairings, tertiary roles, error-container roles, and outline. Prompts and
scripts can therefore use a filled role with its guaranteed-contrast foreground instead of guessing.

## Better application capability detection

Every shell receives:

```sh
TERM_PROGRAM=termux-launcher
TERM_PROGRAM_VERSION=<installed version>
```

The terminal also answers XTVERSION and XTSMGRAPHICS queries with its identity, Sixel register count,
and current geometry. Tools such as chafa and notcurses can choose supported features instead of their
most conservative fallback.

## New reproducible showcase recipes

The repository includes pinned Termux recipes for the Sigye clock and animated-Kitty Fastfetch. They
install under `~/.local` without replacing APT-owned files. See
[Building terminal showcase tools](Building_Terminal_Showcase_Tools.md).

## If you are updating from before 0.2.30

The command palette, native recursive panes and windows, floating panes, scratchpad, durable
workspaces, built-in keyboard, font engine, and Kitty graphics core arrived in the preceding release.
They remain central features rather than 0.2.31 additions:

- [Using the command palette and resizing panes](Launcher_Usage.md#use-the-command-palette)
- [Kitty keyboard, cursor, graphics, and animation protocols](Terminal_Kitty_Protocols.md)
- [Terminal font picker and configuration](Terminal_Fonts.md)

## Other visible changes

- Rounded launcher surfaces use a consistent 20 dp radius, including the status surface.
- Session names allow eight characters, and the scratchpad shell is consistently named `scratch`.
- Terminal notices use a quieter Material chip at the top-right. Background commands are reported
  when leaving a session rather than for every pane/window change inside the visible session.
- Built-in keyboard colors follow live Material roles unless the user pins a swatch or imports a
  complete palette; **Follow theme** returns pinned colors to automatic behavior.
- The refreshed bundled keyboard, Fish template, and guarded installer match the maintained setup.
- Open-Meteo attribution appears next to forecast data. Weather requests use no account or API key
  and send the last known coordinates only after weather is enabled and location is granted.
- CPU and memory values are smoothed, and privileged process sampling slows while the detail card is
  closed.

## Reliability changes worth knowing

The release also fixes pane teardown of background jobs, scratchpad and floating-pane reflow,
wallpaper opacity, CPU-card recovery, notification reply selection, catalog flicker, terminal clipping,
and several background/working-indicator false positives. These fixes do not require migration or new
settings.
