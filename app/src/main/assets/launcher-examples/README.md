# Termux Launcher example configuration

Everything in this directory is a reference copy, rewritten every time the app
starts. Do not edit these files — edit the live ones listed below. Anything you
add here of your own is left alone, but a file with one of the names below is
replaced without warning.

## The files

| Live path | Seeded on install | What it configures |
|---|---|---|
| `~/.termux/termux-launcher-bindings.conf` | yes, if absent | Key bindings, chords, modal keymaps, launching Android apps from a chord |
| `~/.termux/fonts.conf` | yes, if absent | Terminal faces, symbol maps, ligatures, OpenType features, variable axes, cell metrics |
| `~/.termux/keyboard/layout.xml` | no — copy it yourself | In-app keyboard layout, including the space bar's swipe slots |

The two seeded files arrive with every directive commented out, so a fresh
install behaves exactly as it did before they existed. Uncomment what you want.
They are written only when missing, so your edits survive app updates.

`layout.xml` is deliberately not seeded: as soon as that file exists it replaces
the bundled keyboard layout, so creating it should be your decision.

    mkdir -p ~/.termux/keyboard
    cp ~/.termux/launcher/examples/keyboard-layout.xml ~/.termux/keyboard/layout.xml

## Applying changes

    termux-reload-settings

No app restart is needed for any of the three files.

## Full web guides

- Keybindings and multiplexer: https://picklehik3.github.io/termux-launcher-site/#wiki/keybindings
- Complete action and argument reference: https://picklehik3.github.io/termux-launcher-site/#wiki/action-reference
- In-app keyboard layout schema: https://picklehik3.github.io/termux-launcher-site/#wiki/keyboard-layout
- Termux Extra Keys recipes: https://picklehik3.github.io/termux-launcher-site/#wiki/extra-keys

## Starting over

    cp ~/.termux/launcher/examples/fonts.conf ~/.termux/fonts.conf
    cp ~/.termux/launcher/examples/termux-launcher-bindings.conf ~/.termux/

## Launching Android apps from the keyboard

In `~/.termux/termux-launcher-bindings.conf`:

    map ctrl+alt+w app.launch com.whatsapp
    map ctrl+alt+shift+m app.launch Maps
    map ctrl+alt+space>t app.launch org.telegram.messenger

`app.launch` accepts a package name, an app label, or a stable id. An exact
package match wins; otherwise the launcher's fuzzy ranking picks the best match.
Prefer `Ctrl+Alt` or a two-stroke chord over a bare `Alt+<letter>`, which many
shells and editors expect to receive as an Escape prefix.

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
