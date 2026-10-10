# Terminal fonts

This page covers the terminal's font: installing one from the app, the classic Termux `font.ttf`,
and the hand-written `fonts.conf` for symbol ranges, fallbacks, OpenType features and cell metrics.
Start with the picker unless you need one of those. The built-in keyboard's font is a separate
setting; see [Keyboard](Keyboard.md#size-and-look).

## Install a font from the app

The picker is called **Terminal fonts**. Open it from **Settings → Appearance → Terminal fonts**,
from the [command palette](Command_Palette_And_Actions.md) (**Terminal fonts**), or by searching
Settings for "fonts".

The picker has four sections:

- **Setup**: the **Active font** card says what the terminal is using now, `font.ttf` or the
  family managed by `~/.termux/fonts.d/10-launcher.conf`.
- **Tuning**: **Nerd Font icons**, **Ligature policy** and **Weight** (below).
- **Families**: fourteen curated families, among them Maple Mono, Hack, JetBrains Mono, Fira Code,
  Victor Mono and Cascadia Code. A star marks the suggested one, Maple Mono. Each shows its
  download size, faces and full licence before anything downloads. Every download is checked
  against a pinned SHA-256, and a metered connection asks first.
- **Manual control**: **Use font.ttf / Termux:Styling** and **Hand-edited config**.

Installing a family applies its own defaults: the bundled Symbols Nerd Font Mono for icons, the
family's ligature policy, and its features and axis values. The picker writes only these paths:

```text
~/.termux/fonts/<family-id>/regular.ttf, bold.ttf, italic.ttf, bold-italic.ttf, LICENSE.txt
~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf
~/.termux/fonts.d/10-launcher.conf
```

It never creates, overwrites or deletes `~/.termux/font.ttf` or `font-italic.ttf`. Those are
yours and Termux:Styling's, so a pick here changes the terminal and nothing else.

The palette action `fonts.install` installs a family by id, such as `maple-mono`; see
[Command palette and actions](Command_Palette_And_Actions.md#appearance).

## Tune the selected family

- **Font size** sets the text size, in dp, for every pane you have not pinch-zoomed. Step it with
  **−** and **+**, type an exact size, or drag the slider; **Default** goes back to 12 dp. It works
  with any font, and a pane you zoomed keeps its own size.
- **Nerd Font icons** routes the private-use icon ranges to the bundled symbols font.
- **Ligature policy**: keep ligatures, break them only under the cursor, or turn them off.
- **Weight** sets the `wght` axis, for variable families only.

The size is a launcher setting. Each of the other changes rewrites `10-launcher.conf` and applies
at once. A family's line metrics decide the
cell height, so a taller face fits fewer rows, and a full-screen program may need to redraw.
`modify_font cell_height` in `fonts.conf` gets rows back.

**Use font.ttf / Termux:Styling** deletes only `10-launcher.conf`. The downloaded families and
your own `fonts.conf` stay.

## Which file wins

From strongest to weakest:

1. `~/.termux/fonts.conf`: your own settings, read last.
2. `~/.termux/fonts.d/*.conf`: drop-ins, including the picker's `10-launcher.conf`, in filename
   order.
3. `~/.config/kitty/kitty.conf`: its font lines only.
4. `~/.termux/font.ttf` and `font-italic.ttf` (also what Termux:Styling writes), then Android's
   monospace font.

A later setting replaces an earlier one, and anything you leave unset falls through to the next
layer. A fully commented `fonts.conf` changes nothing. A pristine example lives at
`~/.termux/launcher/examples/fonts.conf`; app updates never touch your live `fonts.conf`.

## Use drop-in fragments

Files matching `~/.termux/fonts.d/*.conf` load in filename order before `fonts.conf`, so a tool can
manage one layer while your overrides stay separate. `10-launcher.conf` leaves room for `05-`
fragments it overrides and `20-` fragments that override it.

- Only regular files directly in `fonts.d` are read; subfolders and links pointing outside it are
  skipped.
- At most 32 fragments and 256 KiB across all of them. `fonts.conf` has its own 64 KiB allowance.
- Errors from a fragment start with `fonts.d/<file>: `.

## Share a kitty configuration

If you keep a `~/.config/kitty/kitty.conf`, the terminal reads its font lines, and only those (plus
the cursor-trail lines, see [Graphics, protocols and compatibility](Terminal_Kitty_Protocols.md#cursor-trail)).
One level of `include` is followed. The app never creates the file; an example is at
`~/.termux/launcher/examples/kitty.conf`.

Kitty's own spelling works wherever you write it:

```text
symbol_map U+E1A0-U+E1B6 Herdr Agent Icons Max
font_family Fira Code
bold_font family="Fira Code" style=Bold wght=600
italic_font auto
font_features FiraCode-Retina +zero +onum
modify_font cell_height -2px
```

A family name is looked for in `~/.termux/fonts`, `~/.fonts` and `~/.local/share/fonts` before
Android's own families, so a font copied into place is found by name. `auto` leaves a face to the
terminal. A line is a comment only when it starts with `#`, and a line starting with `\` continues
the one above.

## Write fonts.conf by hand

```text
font_family path=~/.termux/fonts/MyMono-Regular.ttf
bold_font path=~/.termux/fonts/MyMono-Bold.ttf
italic_font path=~/.termux/fonts/MyMono-Italic.ttf
bold_italic_font path=~/.termux/fonts/MyMono-BoldItalic.ttf

symbol_map name=icons U+E000-U+F8FF,U+F0000-U+FFFFD path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf
fallback_font family="Noto Sans CJK SC"

disable_ligatures cursor
font_features regular +zero -liga cv01=2
font_variations regular wght=425

modify_font cell_height 2px
```

Then run `termux-reload-settings`. Paths are the reliable choice; a family name works only when it
can be found. `~/` and quoted values work, `#` starts a comment, and a later duplicate replaces an
earlier one. Each file is capped at 64 KiB, 512 lines and 4,096 characters per line. Font files
over 64 MiB, unreadable or malformed are rejected with a message and a safe fallback.

There are exactly four styles, regular, bold, italic and bold-italic, because that is all a
terminal can ask for. Missing styles are synthesised. This does not limit how many font files are
in use: up to 256 `symbol_map` lines and 8 `fallback_font` entries can be active at once.

## Route symbols deliberately

`symbol_map` sends Unicode ranges to a symbol font without changing the text font. It takes
comma-separated `U+XXXX` or `U+START-U+END` ranges and one `path=` or `family=`. Later overlapping
maps win.

```text
symbol_map name=icons U+E000-U+F8FF,U+F0000-U+FFFFD path=~/.termux/fonts/symbols/SymbolsNerdFontMono.ttf
font_features icons liga=0
```

- A name (`name=`, 1 to 32 of `A-Z a-z 0-9 _ -`, any case) lets `font_features` and
  `font_variations` target one map. `regular`, `bold`, `italic`, `bold_italic` and `symbols` are
  reserved. Unnamed maps share the `symbols` target.
- A name may be declared in a file loaded after the line that uses it; a name never declared is
  reported and its setting dropped.
- Where the symbol font has no glyph for a code point, the character comes from your text font or
  its fallbacks instead of showing an empty box.
- Symbol glyphs are scaled evenly to fit the cell, never squeezed on one axis.

Following kitty, a private-use symbol wider than one cell spreads over the blank cells after it, up
to five, when those blanks look the same (same background, underline and strikethrough). To keep
code points to one cell, or to a ceiling, use kitty's `narrow_symbols`:

```text
narrow_symbols U+E0A0-U+E0A3,U+E0C0-U+E0C7
narrow_symbols U+F0000-U+FFFFD 3
```

## Fallback fonts

`fallback_font` decides which font fills in when the text font lacks a character, instead of
whatever Android picks. Up to 8, tried in order:

```text
fallback_font path=~/.termux/fonts/NotoSansMonoCJK-Regular.otf
fallback_font family="Noto Sans Symbols 2"
```

For each cell the order is: an explicit `symbol_map`, box-drawing geometry, the configured face,
the `fallback_font` chain, then Android's own fallback. A fallback never changes the cell width.

## Box drawing, blocks, braille and Powerline

Box-drawing lines, blocks, shades, braille and sextants are drawn as geometry on whole-pixel cell
edges, so frames and graphs join without hairline gaps at any cell size.

```text
box_drawing synthesize            # default; "font" hands them all back to the font
box_drawing_scale 0.001,1,1.5,2   # thin, light, heavy, very heavy (each above 0, at most 8)
powerline_symbols font            # default; "synthesize" draws the separators too
```

`powerline_symbols synthesize` works only while `box_drawing` is `synthesize`. An explicit
`symbol_map` over a code point always wins. Shades draw as the text colour at 25%, 50% and 75%.

| Drawn as geometry | Contents |
|---|---|
| `U+2500-U+257F` | Box drawing |
| `U+2580-U+259F` | Block elements, quadrants and shades |
| `U+25E2-U+25E5` | Corner triangles |
| `U+2800-U+28FF` | Braille |
| `U+1FB00-U+1FB3B` | Sextants |
| `U+1FB70-U+1FB8F` | Eighth blocks and half shades |
| `U+E0B0-U+E0B7`, `U+E0BA-U+E0BD` | Powerline separators, with `powerline_symbols synthesize` |

Wedges (`U+1FB3C-U+1FB6F`), inverse shades and pattern fills (`U+1FB90-U+1FBFF`), octants
(`U+1CD00-U+1CDE5`) and the other Powerline variants still come from the font.

## Ligatures, features and axes

- `disable_ligatures` is `never` (the default), `cursor` or `always`. It affects programming
  ligatures only, not Arabic, Indic, emoji or combining-mark shaping. Text stays in logical order;
  there is no bidirectional reordering.
- `font_features` and `font_variations` target `regular`, `bold`, `italic`, `bold_italic`,
  `symbols` or a named map. Features are `+tag`, `-tag` or `tag=value`; axes are `tag=value`;
  `none` clears. At most 32 features and 16 axes per target. Use a real variable font when axes
  matter.

## Cell and line metrics

`modify_font` adjusts `cell_width`, `cell_height`, `baseline`, `underline_position`,
`underline_thickness`, `strikethrough_position` and `strikethrough_thickness`. A percentage
replaces the font's value; `px` or a bare number adds pixels; `none` clears. A positive baseline
raises the text; a positive decoration position moves it down.

## Icons outside the terminal

The launcher bundles a symbols-only Nerd Font and uses it on its own surfaces: the status bar, the
window chips, the extra keys and their swipe labels, the built-in keyboard and the extra-keys
editor. A Nerd Font icon in a session name or a key label therefore shows everywhere, not only in
the terminal. Inside the terminal your own font settings still decide.

## When a font setting is ignored

1. Run `termux-reload-settings` after editing.
2. Check `ls ~/.termux/fonts.d/`: a drop-in can set something you did not expect. Remove the
   picker's fragment with **Use font.ttf / Termux:Styling**, not by editing it.
3. Use `path=` for downloaded fonts, and check the file is readable, not empty and under 64 MiB.
4. Check every feature or axis tag is four characters and every named map is declared.
5. Look for the font error message (errors from a drop-in name the file), and try one line at a
   time. Renaming `fonts.conf` tests the lower layers.

Pinch zoom changes the size of the text in one pane, not the font; see
[Resize panes and text](Panes_Windows_And_Sessions.md#resize-panes-and-text).
