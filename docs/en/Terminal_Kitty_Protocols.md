# Kitty protocols and terminal compatibility

Termux Launcher implements modern application-facing terminal protocols directly in the native
terminal. Programs negotiate them; users normally do not enable a compatibility switch.

## Terminal identity and detection

Every Termux shell receives:

```sh
TERM_PROGRAM=termux-launcher
TERM_PROGRAM_VERSION=<installed version>
```

`TERM` remains `xterm-256color` by default. To use a different terminal identity for programs that
string-match it, set this in `~/.termux/termux.properties` — the app seeds that file with the
property documented and commented out, so it is already there to uncomment:

```properties
terminal-term = xterm-kitty
```

The property supplies the default for new sessions. An explicit environment value supplied when a
session is launched still wins, so a caller's `TERM` is not overwritten.

Properties are read from the app's cached copy, so editing the file is not enough on its own. Run
`termux-reload-settings` and then open a new session; a running session keeps the `TERM` it started
with. Verified on device: a session opened straight after the file edit still reported
`xterm-256color`, while one opened after `termux-reload-settings` reported `xterm-kitty`.

XTVERSION reports `termux-launcher(version)`. XTSMGRAPHICS reports the Sixel color-register count and
geometry for the current screen. Capability detectors such as chafa and notcurses can therefore pick
a supported renderer instead of relying on a conservative terminal-name fallback.

### Telling a TUI it is in kitty

Some programs skip the probe and only switch on their kitty features when the environment says
`TERM_PROGRAM=kitty`. Neovim 0.12 image plugins such as md-render.nvim and image.nvim are the common
case: they read the variable, find `termux-launcher`, and draw nothing. The terminal speaks the
protocols anyway, so the fix is to tell those programs what they want to hear.

Per program, which keeps the rest of the shell honest:

```sh
TERM_PROGRAM=kitty nvim README.md        # bash, zsh, fish 3.1+
```

As an alias or abbreviation so you never type it:

```sh
alias nn='TERM_PROGRAM=kitty nvim'                 # bash or zsh, in ~/.bashrc or ~/.zshrc
abbr -a nn "TERM_PROGRAM=kitty nvim"               # fish, in ~/.config/fish/conf.d/*.fish
```

For every program, in the shell rc:

```sh
export TERM_PROGRAM=kitty
```

What changes and what does not:

- Programs that gate pictures or the keyboard protocol on the variable start using them. Both work
  here.
- The XTVERSION reply stays `termux-launcher(version)`, so features that check the real terminal
  version stay off; md-render.nvim, for one, enables text sizing only for a kitty version, even
  though the terminal supports it (see [Text sizing](#text-sizing-osc-66)). Nothing pretends to be
  a kitty release it is not.
- `TERM_PROGRAM_VERSION` still carries the launcher's version; a program that reads both will see a
  kitty name with a non-kitty version. That is the one inconsistency this setting introduces.
- Neovim 0.13 and newer sends the graphics query instead of reading the variable, and the terminal
  answers it. Once your Neovim is 0.13 the variable is only there for older tools.

The in-app Help has the same one-line recipe under **Pictures in the terminal**.

## Kitty keyboard protocol

Supported negotiation includes:

- disambiguated escape codes;
- key press, repeat, and release events;
- alternate key values;
- all-keys-as-escape-codes mode;
- associated text; and
- independent flags and bounded mode stacks for main and alternate screens.

Applications opt in and out themselves. Programs that do not negotiate the protocol continue through
the normal Termux key encoder.

## Multiple cursors

The Kitty multiple-cursors protocol supports point and rectangular cursors, cursor shape, and color.
This is independent of the launcher's animated input cursor trail.

## Kitty graphics Tier 2

The terminal supports:

- direct PNG and raw RGB/RGBA transmission;
- zlib-compressed raw pixels and chunked transfers;
- stored images by image ID or number;
- placements with source cropping, cell scaling, sub-cell offsets, and z-index;
- Unicode placeholder virtual placements (`U=1`), including row/column diacritics, 32-bit image
  IDs, placement IDs in underline colour, and left-cell inheritance;
- acknowledgements, quiet modes, and delete forms; and
- text-safe negative-z placement: text remains visible while the image occupies surrounding blanks.

Examples that work without extra launcher configuration include:

```sh
timg -pk image.png
chafa -f kitty image.png
```

Yazi can also use Kitty image previews when configured to select that backend.

Unicode placeholders keep U+10EEEE cells as ordinary terminal text while drawing slices of the
stored image through them. This lets tmux and full-screen editors move or redraw the cells without
understanding image state, which is the path used by Neovim image integrations inside tmux.

## Kitty graphics animation

Animation support includes frame upload, partial-frame rectangles, base-frame and background-color
composition, frame gaps, animation control, composition, and frame deletion.

Both client-driven and terminal-driven playback are supported. With terminal-driven playback, a GIF
uploaded as Kitty frames keeps animating on the terminal's own clock after the sender exits.

The repository's patched Fastfetch recipe demonstrates this path without replacing APT-owned
files; it places its logo through the Unicode placeholders above, so the animation runs on
cells that are ordinary text.
See [Building terminal showcase tools](Building_Terminal_Showcase_Tools.md).

## Sixel and iTerm paths

Existing Sixel and iTerm bitmap rendering remain available alongside Kitty graphics. Applications can
choose their preferred protocol from capability replies or explicit command-line options.

## Color scheme notifications

Programs can follow the terminal between dark and light instead of guessing from `$COLORFGBG`.

- `CSI ? 996 n` asks for the current preference. The reply is `CSI ? 997 ; 1 n` when the default
  background is dark and `CSI ? 997 ; 2 n` when it is light, and it is sent whether or not the
  notification mode below is enabled.
- `CSI ? 2031 h` turns on unsolicited notifications and `CSI ? 2031 l` turns them off. While the mode
  is on, the same `CSI ? 997 ; Ps n` report is sent once each time the default background crosses
  between dark and light, whether the change came from the app's color scheme, from `OSC 11`, or from
  a reset such as `OSC 111` or `OSC 104`. A color change that stays on the same side is silent.
  `CSI ? 2031 $ p` (DECRQM) answers with the mode's state, and a terminal reset turns it back off.
- `OSC 4 ; <index> ; ? ST` reports an indexed color as `OSC 4 ; <index> ; rgb:RRRR/GGGG/BBBB ST`,
  matching the existing `OSC 10`, `OSC 11` and `OSC 12` query replies.

nvim 0.11, fish 4.3 and tmux 3.6 use these reports to restyle themselves when the terminal's scheme
changes.

## Text sizing (OSC 66)

`ESC ] 66 ; s=2 ; Heading ESC \` draws "Heading" twice as large, across two rows and twice the
columns. The keys follow kitty's text sizing protocol: `s` 1 to 7 is the scale and the number of
rows the text takes; `w` 0 to 7 forces a width in cells before scaling (0 measures the text);
`n` and `d` draw the text at n/d of the block height, with `v` (0 top, 1 bottom, 2 centre) and
`h` (0 left, 1 right, 2 centre) placing it inside the block. With `w` unset every character is its
own block; with `w` set the whole text is one block and anything past `w` cells is cut.

What a program can rely on:

- The cursor moves `s × w` cells to the right on the same row, so the usual detection, writing a
  `w=2` character and reading the cursor position, answers "supported". No environment variable is
  involved.
- Writing over the top-left cell of a block removes the whole block; writing over any other cell
  blanks that cell. Erase, insert and delete sequences and scrolling inside a region remove every
  block they touch. A block larger than the screen is discarded.
- The cursor covers the whole block when it stands on any of its cells. Selecting any part of a
  block selects all of it, and copying yields its text once.
- When the pane becomes too narrow for a block, the text is shown at normal size on its first row
  and grows back when the pane is wide enough again. Rotating the phone keeps blocks whole.

Full-screen programs redraw on resize anyway; the narrow-pane rule matters only for text already in
the scrollback.

## Current boundaries

- File (`t=f`) and temporary-file (`t=t`) transmissions are accepted; a `t=t` file is deleted only when its path carries `tty-graphics-protocol` and sits in a temporary directory. Shared-memory (`t=s`) transmission is not implemented: Android has no `shm_open`.
- Unsupported or excessive requests return bounded protocol errors rather than consuming unbounded
  memory.
- Image geometry follows terminal cells, so changing a pane's size or font metrics may cause the
  sending application to redraw or resend an image.

For the complete protocol-level feature list, see
[Rendering and application compatibility](Terminal_Modernization.md#rendering-and-application-compatibility).
