# Touch, links and the clipboard

How your finger works in the terminal, how links open, how to find text in the scrollback, and how
copying and pasting work, including the keyboard's clipboard history.

## Touch works like a mouse

Touch is tuned for full-screen programs, not only for a shell prompt.

- **Drag scrolls.** Inside a program that tracks the mouse the drag becomes scroll-wheel events,
  so lists in `htop`, lazygit or vim scroll as expected.
- **Tap clicks** when the running program tracks the mouse.
- **Hold for a moment, then drag** to hold the mouse button. A small haptic hands the finger to the
  program; lift and that cell is clicked, or drag and the button stays down (a second haptic marks
  it), so you can select in vim, move tmux splits or resize a program's panes. If the program did
  not ask for motion, dragging sends nothing and the lift still clicks.
- **Keep holding to select text.** A different haptic says the hold went further: **Copy**,
  **Paste** and **More** open where your finger landed. **More** leads to the rest of the actions.
- **In a plain shell** there is no mouse to hand over, so the hold starts text selection at the
  first haptic, and a drag before it scrolls.
- **Two fingers** scroll or pinch. **Pinch** changes the focused pane's text size, with filtering
  so a two-finger scroll does not zoom by accident.
- **A brisk two-finger flick** on a split pane swaps it with the pane across the edge you flick
  towards. See
  [Panes, windows and sessions](Panes_Windows_And_Sessions.md#swap-panes-with-a-two-finger-flick).

Ctrl, Alt and Shift go along with a mouse click, from the extra-keys row or a hardware keyboard,
the way xterm sends them. A latched Ctrl covers one click, so **Ctrl+tap** lets a program open its
own links.

## Mouse mode

**Mouse mode** turns every touch into the mouse for programs that take one. A finger down is the
left button at that cell, held as it moves and released where it lifts; two fingers turn the
wheel, and a fast lift keeps it turning. A program that has not asked for the mouse gets nothing,
and two fingers scroll the transcript as usual. A small mouse at the end of the status bar shows
the mode is on; tap it to switch the mode off.

Switch it with the **Mouse** key on the extra-keys row, **Mouse mode** in the
[command palette](Command_Palette_And_Actions.md), or a `tool:mouse.toggle` key or binding. On the
Display place the same action swaps the keyboard for a touchpad; see
[The Linux display](X11_Display.md).

## Links

Web addresses in the output and OSC 8 hyperlinks from programs are underlined. Tap one and a small
**Copy** / **Open** strip appears above the line with the full address, before anything opens.

- The link is read the moment your finger lifts, so a program that keeps redrawing does not move it
  out from under the tap.
- Over a program that tracks the mouse, the tap still goes to the program as its click, and the
  strip appears as well. Hold or latch **Shift** for the tap and nothing goes to the program; only
  the strip appears.
- An address a program wrapped by hand across rows, even inside a padded box with a border, is
  followed whole. A symbol right after an address, such as fish's `⏎`, is not taken as part of it.
- Only `http`, `https`, `mailto`, `tel`, `sms`, `geo`, `ftp` and `ftps` links can be opened. Others,
  `file` included, can only be copied.

`terminal-onclick-url-open = true` in `~/.termux/termux.properties` (the launcher's default) is what
underlines and opens plain addresses. Set it to `false` for upstream Termux behaviour, where a URL
is reached through **Select URL**.

## Find things on screen

- **Quick select** (`Ctrl+Alt+U`) labels the URLs, paths, hashes and `file:line` references on
  screen with keys. Press a label to open a URL or copy anything else; hold Shift while choosing a
  URL to copy it instead.
- **Search scrollback** (`Ctrl+Alt+S`) searches the focused pane's history without regard to case
  and jumps to the match you pick.
- **Select URL** lists the links in the scrollback.
- **Select at cursor** starts a selection on the word under the shell's cursor; **Select all**
  selects the whole buffer. **Copy selected text** and **Share selected text** then act on it.

## Jump between prompts

**Jump to previous prompt** and **Jump to next prompt** in the palette need shell marks (OSC 133).
fish 4 sends them on its own. For bash or zsh, source the script the app keeps up to date:

```sh
# in ~/.bashrc
source ~/.termux/shell-integration/termux-launcher.bash
# or in ~/.zshrc
source ~/.termux/shell-integration/termux-launcher.zsh
```

The app never edits your rc files. Open a new shell afterwards; scrollback from before has no marks.

## Copy and paste

- Copy a selection with **Copy**, or **Copy selected text** in the palette.
- Paste with the paste key on the keyboard, **Paste** in the selection menu, or **Paste** in the
  palette (`Ctrl+Alt+V` with **Split-pane controls** off).
- **Settings → Terminal → Clipboard → Clean up clipboard text** ("Trim trailing spaces and
  single-line paste newlines."), on by default, tidies copied text.

The terminal, the keyboard's paste key, the Linux display and every Android app share one
clipboard.

## Programs and the clipboard

A program in the terminal can write the clipboard with the OSC 52 escape or kitty's OSC 5522 (as
`kitten clipboard` does); a write from a pane needs the launcher on screen. Reading it back needs
the launcher on screen and **Settings → Terminal → Clipboard → Let programs read the clipboard**
("Programs in the terminal can paste what you copied."), on by default. Turn it off if you would
rather programs could not read what you copied. `launcherctl clipboard copy` writes even while the
launcher is in the background. See [Programs inside the terminal](Programs_Inside_The_Terminal.md).

## Clipboard history

Swipe up and to the right on the **Ctrl** key of the built-in keyboard to open its clipboard
history in place of the keys. **Clipboard history** in the palette, or a `tool:keyboard.clipboard`
key, opens the same panel.

It lists what you copied **in the launcher**: a selection in the terminal, the keyboard's copy and
cut keys, a link or a Quick select item you copy, a yank in find mode, and text a program copies with
`launcherctl clipboard copy` or OSC 52. What you copy in other apps is not collected, though the
paste key still pastes whatever the phone's clipboard holds.

- Tap an item to paste it and put it back on the clipboard.
- Pin an item to keep it above the rest and across restarts. Up to 20 pins are kept.
- Recent items live in memory only, about thirty of them. Copies over 16 KB reach the clipboard but
  are not listed.
- **Clear** asks for a second tap and never removes pins. The keyboard pill in the panel's corner
  brings the keys back.
