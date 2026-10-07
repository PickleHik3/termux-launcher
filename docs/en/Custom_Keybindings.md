# Custom keybindings

One file, `~/.termux/termux-launcher-bindings.conf`, changes or adds launcher shortcuts: run any
action, type text, send a key, launch an app, or chain several strokes. This page is the file's
grammar. The defaults it starts from are in [Keyboard shortcuts](Keyboard_Shortcuts.md).

## Edit and apply

The app seeds the file on install, every line commented out, and never overwrites it. A pristine
copy is kept in `~/.termux/launcher/examples/termux-launcher-bindings.conf`. After an edit:

```sh
termux-reload-settings
```

Whatever the file binds is what the app shows: the palette's shortcut column, the shortcut legend
and the keys that light up while Ctrl and Alt are latched all read the same bindings.

## Lines

```text
# Comments start with #.
map ctrl+alt+g pane.equalize
map --when splits-on ctrl+alt+e pane.equalize
unmap ctrl+alt+u          # removes Quick select from Ctrl+Alt+U
map ctrl+alt+space>g pane.rotate
```

- `map <keys> <action> [arguments]` binds keys to an action.
- `unmap <keys>` removes every binding for those keys.
- `leader <stroke>` sets a prefix key (below).
- `#` starts a comment anywhere outside quotes.

## Writing keys

- A stroke is modifiers and a key joined by `+`: `ctrl+alt+r`. Modifiers are `ctrl` (or
  `control`), `alt` and `shift`, in any case.
- **An upper-case letter means Shift.** `Ctrl+Alt+R` is `ctrl+alt+shift+r`, a different binding
  from `ctrl+alt+r`. Multi-letter key names are not affected: `Left` and `left` are the same.
- Key names: `a` to `z`, `0` to `9`, `f1` to `f12`, `left`, `right`, `up`, `down`, `space`,
  `tab`, `enter`, `escape`, `backspace`, `delete`, `home`, `end`, `pageup`, `pagedown`, `minus`,
  `equals`, `plus`, and the characters `` ` `` `[` `]` `/` `\` `;` `'` `,` `.` written as
  themselves (``ctrl+alt+` ``).
- `>` joins strokes into a chord of up to 8 strokes: `ctrl+alt+space>p`.

## When a binding applies

`--when` takes `always` (the default), `splits-on` or `splits-off`, matching the
**Split-pane controls** setting:

```text
map --when splits-off ctrl+alt+c session.new
```

## Actions

- **An action id** from [Command palette and actions](Command_Palette_And_Actions.md), with its
  arguments after it.
- **`send-text "…"`** types text into the focused shell. Inside quotes `\n`, `\r`, `\t` and `\e`
  (Escape) work; single quotes take the text literally.
- **`send-key <stroke>`** sends one key: `send-key ctrl+c`.
- **`pop-mode`** leaves the current mode (below).

Repeat a `map` line with the same keys to run several actions in order:

```text
map ctrl+alt+j send-text "cd ~/src\n"
map ctrl+alt+j send-text "git status\n"
```

## Arguments

Positional words fill an action's required arguments in order; `name=value` reaches any argument.
Values are checked, so an unknown value or a number out of range is reported rather than run.
Quote a value with spaces.

```text
map ctrl+alt+shift+g pane.layout grid
map ctrl+alt+shift+e pane.move_to_edge left
map ctrl+alt+shift+n session.new name=build failsafe=false
map ctrl+alt+shift+w window.rename "build logs"
```

A stroke's own direction or digit still applies (the arrow keys pass a direction, the digit keys
an index); an argument written in the file wins over it.

## Names in the legend

`--label "Name"` (1 to 32 characters) is what the shortcut legend prints. Without it the legend
prints the action's title, so every app shortcut would read "Launch app". In a multi-line binding
the first label wins.

```text
map --label WhatsApp ctrl+alt+shift+a app.launch com.whatsapp
map --label "Repo status" ctrl+alt+j send-text "cd ~/src\n"
```

`app.launch` takes a package name, an app label or a stable id; an exact package match wins,
otherwise the best fuzzy match.

## Bind an app from the palette

Find the app in the palette's **Apps** section, hold its row (or press `Ctrl+Alt+Enter` on it), then
press a key combination: `⏎` saves, `⌫` clears, `Esc` cancels. The palette writes the line under a
managed header in the bindings file, labelled with the app's name, keeps your comments and order,
and applies it at once.

- A bare key is refused; add Ctrl, Alt or Shift.
- `⏎`, `Esc` and `⌫` cannot be captured. Bind those in the file.
- If the combination is taken, the palette says by what and saves anyway, replacing it.

## A prefix key

```text
leader ctrl+space
```

Every default and custom `ctrl+alt+…` binding then also answers to the prefix followed by the same
key: `Ctrl+Space` then `M` opens the action sheet, `Ctrl+Space` then `Shift+P` the palette. The
`Ctrl+Alt` strokes keep working. A sequence you write yourself is never overwritten, and only the
first `leader` line counts. While **Android language shortcut** is on (Settings → Keyboard →
Hardware keyboard), `Ctrl+Space` goes to Android instead.

## Modes

A key can enter a named mode, in which single keys run actions:

```text
map --new-mode nav --timeout 10 --on-unknown passthrough --on-action keep ctrl+alt+shift+v
map --mode nav h window.previous
map --mode nav l window.next
map --mode nav q pop-mode
map --mode nav escape pop-mode
```

| Option | Values | Default |
|---|---|---|
| `--new-mode` | a name: 1 to 32 of `A-Z a-z 0-9 _ . -` | |
| `--timeout` | seconds, 0 to 3600 | 2 |
| `--on-unknown` | `beep`, `ignore`, `end`, `passthrough` | `beep` |
| `--on-action` | `keep` or `end` | `keep` |

Modes can stack; `pop-mode` leaves the top one. An overlay shows a pending chord and the active
mode.

## Defaults you replace

Mentioning a key sequence with `map` or `unmap` replaces **every** default for that sequence. A
line like `map ctrl+alt+w app.launch com.whatsapp` is valid, but it removes **Kill focused pane**
from `Ctrl+Alt+W`. Check [Keyboard shortcuts](Keyboard_Shortcuts.md) before taking a stroke;
`Ctrl+Alt+Enter`, `Ctrl+Alt+Shift+S`, `Ctrl+Alt+Shift+R` and `Ctrl+Alt+Shift+1` to `9` are all taken.

## Errors and limits

- A bad line is skipped and the rest of the file still loads. Errors are logged and summarised in
  a short message.
- The file may be at most 256 KiB, 4,096 lines and 4,096 characters per line. Going over any of
  these discards the **whole file**: no custom binding loads and the defaults apply.
- **Key inspector** in the palette shows which binding claimed a key.

Keyboard swipes are not set here. They live in `~/.termux/keyboard/layout.xml`
([Keyboard](Keyboard.md#your-own-layout)); on-screen keys are in [Extra keys](Extra_Keys.md).
