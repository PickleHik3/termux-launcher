# Configuration files

Every file the launcher reads or writes in your home directory, what it is for, and which page
explains it. Edit the live files, then run `termux-reload-settings`; no app restart is needed.

## What the app seeds

Three files are written on install, only when they are missing, so an update never overwrites
your edits:

- `~/.termux/termux-launcher-bindings.conf`
- `~/.termux/fonts.conf`
- `~/.termux/termux.properties`

They arrive with their settings commented out, so a fresh install behaves as if they were not
there; uncomment what you want. The one live line is `terminal-onclick-url-open = true` in
`termux.properties`, which matches the launcher's default. If you already keep a
`~/.config/termux/termux.properties`, the app seeds no `~/.termux/termux.properties`, so yours
stays in charge.

`~/.termux/keyboard/layout.xml` is never seeded, because the moment it exists it replaces the
bundled keyboard layout. `~/.config/kitty/kitty.conf` is never created either.

## The examples folder

`~/.termux/launcher/examples/` holds reference copies, rewritten every time the app starts:

| File | Copy it to |
|---|---|
| `README.md` | (read it; a summary of this page) |
| `termux-launcher-bindings.conf` | `~/.termux/termux-launcher-bindings.conf` |
| `fonts.conf` | `~/.termux/fonts.conf` |
| `termux.properties` | `~/.termux/termux.properties` |
| `keyboard-layout.xml` | `~/.termux/keyboard/layout.xml` |
| `kitty.conf` | `~/.config/kitty/kitty.conf` |

Nothing in this folder is read as configuration. Files you add there yourself are left alone, but
one with a shipped name is replaced. To start over, copy a file back:

```sh
cp ~/.termux/launcher/examples/termux-launcher-bindings.conf ~/.termux/
mkdir -p ~/.termux/keyboard
cp ~/.termux/launcher/examples/keyboard-layout.xml ~/.termux/keyboard/layout.xml
termux-reload-settings
```

## Files you edit

| Path | What it does | Details |
|---|---|---|
| `~/.termux/termux.properties` | Terminal properties: `TERM` (`terminal-term`), volume and back keys, the extra-keys row (`extra-keys`, `extra-keys2`), cursor, scrollback, margins, link taps | [Extra keys](Extra_Keys.md#write-the-row-by-hand) |
| `~/.config/termux/termux.properties` | Read only when `~/.termux/termux.properties` is absent; only one properties file is ever read | |
| `~/.termux/termux-launcher-bindings.conf` | Shortcuts, chords, modes, app launching | [Custom keybindings](Custom_Keybindings.md) |
| `~/.termux/keyboard/layout.xml` | Replaces the whole built-in keyboard layout | [Keyboard](Keyboard.md#your-own-layout) |
| `~/.termux/fonts.conf` | Your terminal font settings; read last, so it wins | [Terminal fonts](Terminal_Fonts.md) |
| `~/.termux/fonts.d/*.conf` | Font drop-ins, read before `fonts.conf` in name order | [Terminal fonts](Terminal_Fonts.md#use-drop-in-fragments) |
| `~/.config/kitty/kitty.conf` | Only its font and cursor-trail lines are read | [Terminal fonts](Terminal_Fonts.md#share-a-kitty-configuration) |
| `~/.termux/font.ttf`, `~/.termux/font-italic.ttf` | The classic Termux font files, also written by Termux:Styling | [Terminal fonts](Terminal_Fonts.md) |
| `~/.termux/colors.properties` | The terminal colour scheme, also written by Termux:Styling; ignored while **Wallpaper colors** is on | [Look and themes](Look_And_Themes.md) |
| `~/.termux/launcher-theme.properties` | Overrides single launcher colours, one token per line, even with **Wallpaper colors** on | [Look and themes](Look_And_Themes.md) |
| `~/.termux/theme-templates/<id>/` | Your own entries for "Tools that follow the terminal colours": a `template.properties`, the file to render and its hooks | [Look and themes](Look_And_Themes.md) |
| `~/.termux/app-categories.conf` | The app drawer's categories, one section per category, one package per line | [Home screen and apps](Home_Screen_And_Apps.md) |
| `~/.termux/boot/` | Scripts run at boot by the launcher's Termux:Boot companion | |

## Files the app writes

| Path | What it is |
|---|---|
| `~/.termux/fonts.d/10-launcher.conf` | The font picker's choice; the picker rewrites it, so do not hand-edit it |
| `~/.termux/fonts/` | Font families the picker installed, each with its `LICENSE.txt`, plus the bundled symbols font |
| `~/.termux/material-colors.sh`, `.properties` | The launcher's current colour roles, for prompts and scripts |
| `~/.termux/material-colors-dark.sh`, `.properties` and `material-colors-light.sh`, `.properties` | The same roles for each mode |
| `~/.termux/theme-templates/.applied` | The list of templates the launcher has applied |
| `~/.termux/workspaces/<name>.json` | Saved workspaces ([Panes, windows and sessions](Panes_Windows_And_Sessions.md#workspaces)) |
| `~/.termux/shell-integration/termux-launcher.bash`, `.zsh` | Prompt marks for bash and zsh; source one from your rc file ([Touch, links and the clipboard](Touch_Links_And_Clipboard.md#jump-between-prompts)) |
| `~/.termux/launcher/examples/` | The reference copies above |
| `~/.launcherctl/endpoint`, `~/.launcherctl/token` | Where `launcherctl` finds the app ([LauncherCtl](LauncherCtl.md)) |

## Telling programs where they run

Every shell gets `TERM_PROGRAM=termux-launcher` and `TERM_PROGRAM_VERSION` set to the installed
version, and the terminal answers XTVERSION and XTSMGRAPHICS queries, so programs such as chafa and
notcurses can pick what they support. `TERM` stays `xterm-256color` unless you set `terminal-term`;
a running session keeps the `TERM` it started with. See
[Graphics, protocols and compatibility](Terminal_Kitty_Protocols.md#terminal-identity-and-detection).

## Volume keys

The volume keys change the volume, so a home screen does not swallow the rocker. Set
`volume-keys = virtual` in `termux.properties` for upstream Termux behaviour, where they act as Ctrl
and Fn.
