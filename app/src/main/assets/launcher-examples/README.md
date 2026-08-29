# Termux Launcher example configuration

Everything in this directory is a reference copy, rewritten every time the app
starts. Do not edit these files — edit the live ones listed below. Anything you
add here of your own is left alone, but a file with one of the names below is
replaced without warning.

## The files

| Live path | Seeded on install | What it configures |
|---|---|---|
| `~/.termux/termux-launcher-bindings.conf` | yes, if absent | Key bindings, chords, modal keymaps, launching Android apps from a chord |
| `~/.termux/fonts.conf` | yes, if absent | Terminal faces, symbol maps, fallback chain, ligatures, OpenType features, variable axes, box drawing, cell metrics |
| `~/.termux/termux.properties` | yes, if absent | `TERM`, volume and back keys, extra keys, cursor, scrollback, margins, colours, app behaviour |
| `~/.termux/keyboard/layout.xml` | no — copy it yourself | In-app keyboard layout, including the space bar's swipe slots |

The three seeded files arrive with every directive commented out, so a fresh
install behaves exactly as it did before they existed. Uncomment what you want.
They are written only when missing, so your edits survive app updates.

`termux.properties` has one extra rule: only one properties file is ever read.
`~/.termux/termux.properties` wins and `~/.config/termux/termux.properties` is
read only when it is absent — so if you already keep yours at that second path,
the app leaves it alone and seeds nothing.

## Fonts: three tiers

You do not have to write `fonts.conf` at all. Settings > Appearance > Terminal
fonts lists seven families — a star marks the suggested one — and installing any
of them writes the faces plus Nerd Font icons and
`~/.termux/fonts.d/10-launcher.conf` itself. It never touches `~/.termux/font.ttf`
or `font-italic.ttf`, so a pick there changes the terminal only.

Precedence, from strongest to weakest:

1. `~/.termux/fonts.conf` — yours, read last, therefore final
2. `~/.termux/fonts.d/*.conf` — read first, in filename order; `10-launcher.conf`
   is the app-managed one
3. `~/.termux/font.ttf` and `font-italic.ttf` — the native Termux contract, also
   what Termux:Styling writes

So the picker and a hand-written `fonts.conf` can coexist: set only the
directives you care about here and the fragment supplies the rest. Errors from a
drop-in are prefixed `fonts.d/<file>: ` in the log and toast.

`layout.xml` is deliberately not seeded: as soon as that file exists it replaces
the bundled keyboard layout, so creating it should be your decision.

    mkdir -p ~/.termux/keyboard
    cp ~/.termux/launcher/examples/keyboard-layout.xml ~/.termux/keyboard/layout.xml

## Applying changes

    termux-reload-settings

No app restart is needed for any of these files. One caveat: a running terminal
session keeps the environment it started with, so `terminal-term` reaches only
sessions opened after the reload.

## Full web guides

- Keybindings and multiplexer: https://picklehik3.github.io/termux-launcher-site/#wiki/keybindings
- Complete action and argument reference: https://picklehik3.github.io/termux-launcher-site/#wiki/action-reference
- In-app keyboard layout schema: https://picklehik3.github.io/termux-launcher-site/#wiki/keyboard-layout
- Termux Extra Keys recipes: https://picklehik3.github.io/termux-launcher-site/#wiki/extra-keys

## Starting over

    cp ~/.termux/launcher/examples/fonts.conf ~/.termux/fonts.conf
    cp ~/.termux/launcher/examples/termux-launcher-bindings.conf ~/.termux/
    cp ~/.termux/launcher/examples/termux.properties ~/.termux/

## Launching Android apps from the keyboard

In `~/.termux/termux-launcher-bindings.conf`:

    map ctrl+alt+w app.launch com.whatsapp
    map ctrl+alt+shift+m app.launch Maps
    map ctrl+alt+space>t app.launch org.telegram.messenger

`app.launch` accepts a package name, an app label, or a stable id. An exact
package match wins; otherwise the launcher's fuzzy ranking picks the best match.
Prefer `Ctrl+Alt` or a two-stroke chord over a bare `Alt+<letter>`, which many
shells and editors expect to receive as an Escape prefix.

An upper-case letter in a sequence is Shift, so `Ctrl+Alt+R` and `Ctrl+Alt+r`
are two different bindings (`ctrl+alt+shift+r` and `ctrl+alt+r`). Modifier names
and multi-character key names stay case-insensitive.

Swipe slots on the in-app keyboard cannot launch apps: a `tool:` key carries no
arguments. Use a chord.

## Diagnostics

- Errors in either `.conf` file are logged and summarized in a toast; valid
  lines stay active and defaults are kept where a line could not be used.
- The command palette (`Ctrl+Alt+Shift+P`) lists every action id these files can
  name.
- **Key inspector** in the palette reports which binding claimed a key and what
  bytes reached the shell.

Full project reference: `docs/en/Terminal_Modernization.md` in the project repository.
