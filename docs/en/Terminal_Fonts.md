# Terminal fonts

Termux Launcher supports a no-shell font picker, traditional Termux `font.ttf`, and a layered
power-user configuration. Start with the picker unless you need explicit fallback ranges, OpenType
features, or custom metrics.

## Install a font from Settings

Open **Settings → Look → Terminal fonts**. Version 0.2.31 offers fourteen curated families with:

- download size and upstream license shown before installation;
- a pinned SHA-256 check for every download;
- regular, bold, italic, and bold-italic faces where the family provides them;
- family-specific ligature and feature defaults; and
- optional Nerd Font icon routing through the bundled Symbols Nerd Font Mono.

Maple Mono is the suggested family, but the choice changes only the terminal. The picker writes font
files under `~/.termux/fonts/` and manages this fragment:

```text
~/.termux/fonts.d/10-launcher.conf
```

It never creates, overwrites, or deletes `~/.termux/font.ttf` or `font-italic.ttf`.

## Tune the selected family

The picker exposes:

- **Nerd Font icons:** route common private-use icon planes to the bundled symbol face;
- **Ligature policy:** always shape, unfuse under the cursor, or disable ligatures; and
- **Weight:** set the `wght` axis for variable families.

Changes rewrite only the managed fragment and apply to the terminal immediately. A font with taller
line metrics fits fewer rows; a full-screen TUI may need to reflow after the change.

Choose **Use font.ttf / Termux:Styling** to delete only `10-launcher.conf`. Downloaded families and
your hand-written configuration stay in place.

## Understand configuration priority

The terminal resolves active directives in this order, from highest to lowest priority:

1. `~/.termux/fonts.conf` — your hand-written overrides;
2. `~/.termux/fonts.d/*.conf` — app and third-party fragments in filename order; and
3. `~/.termux/font.ttf`, `font-italic.ttf`, Termux:Styling, or Android monospace.

An active directive in `fonts.conf` overrides the same setting from every fragment. Unspecified
settings still inherit from lower layers. A fully commented `fonts.conf` changes nothing.

The app refreshes pristine, commented examples under:

```text
~/.termux/launcher/examples/fonts.conf
```

App updates do not overwrite the live `fonts.conf`.

## Use drop-in fragments

Files matching `~/.termux/fonts.d/*.conf` load in filename order before `fonts.conf`. This lets the
font picker or another tool manage one layer while personal overrides remain separate.

Limits keep accidental configuration growth bounded:

- at most 32 fragments;
- at most 256 KiB across all fragments; and
- a separate 64 KiB limit for `fonts.conf`.

## Configure faces and fallback

A minimal manual configuration can name paths or Android system families:

```text
font_family path=~/.termux/fonts/MyMono-Regular.ttf
bold_font path=~/.termux/fonts/MyMono-Bold.ttf
italic_font path=~/.termux/fonts/MyMono-Italic.ttf
bold_italic_font path=~/.termux/fonts/MyMono-BoldItalic.ttf

fallback_font family="Noto Sans CJK SC"
fallback_font family="Noto Color Emoji"
```

Up to eight ordered fallback faces are supported. Coverage is tested against the cluster's base code
point, and a fallback never changes the terminal cell width.

## Route symbols deliberately

Use `symbol_map` to send selected Unicode ranges to a symbol face without changing the text font:

```text
symbol_map name=icons U+E000-U+F8FF,U+F0000-U+FFFFD path=~/.termux/fonts/SymbolsNerdFontMono-Regular.ttf
font_features icons liga=0
```

A named map lets `font_features` and `font_variations` target one map rather than every symbol font.
Unnamed maps continue using the shared `symbols` target.

Symbol glyphs are scaled uniformly until they meet the cell box, never squeezed on one axis. Nerd
Font glyphs are drawn on a full em square while a text cell is narrower than its em — Maple Mono's
is 0.6 em — so a symbol confined to one cell ends up markedly shorter than the capitals beside it.

Following kitty, a private-use symbol whose own glyph is wider than one cell, and which is followed
by blanks that paint the same, is drawn across those blanks instead. It asks for
`ceil(advance / cell width)` cells, takes as many of them as there are blanks, and never exceeds
five. A glyph that already fits its cell asks for one and is left where it is.

A blank is a space or an en-space (U+2002). "Paints the same" means the same background and the same
underline style, underline and strikethrough — all a space can show. Foreground colour, bold and
italic are ignored, so a coloured icon followed by an uncoloured separator still expands. A symbol
against real text, or against a blank with a different background or decoration, stays in its cell.

To keep specific code points narrow, use `narrow_symbols` — kitty's directive, same syntax:

```text
narrow_symbols U+E0A0-U+E0A3,U+E0C0-U+E0C7
narrow_symbols U+F0000-U+FFFFD 3
```

The trailing number is the cell ceiling and defaults to 1, the maximum is 5, and for a code point
matched by several lines the last one wins. Note that the Powerline separators in `U+E0B0-U+E0B7`
and `U+E0BA-U+E0BD` are drawn as geometry rather than shaped as text, so they never expand and need
no rule; `powerline_symbols font` hands them back to the face and brings them under this rule.

## Box drawing, blocks, braille, and Powerline

By default the terminal draws box-drawing lines, blocks, shades, braille, sextants, and Powerline
separators as geometry aligned to integer cell edges. This prevents the hairline gaps produced when
font glyphs do not completely fill a cell.

Manual controls include:

```text
box_drawing font
box_drawing_scale 0.001,1,1.5,2
powerline_symbols font
```

Use the `font` values when a specific typeface's own glyph design is preferred. Geometric synthesis
takes priority over overlapping symbol maps so a broad Nerd Font range cannot disable it silently.

## Features, variable axes, and metrics

`font_features` controls OpenType tags by face or named symbol target. `font_variations` controls
variable axes such as `wght`, and metrics directives can tune cell height, baseline, underline, and
strike position. Invalid or excessive settings are ignored with a visible configuration notice and
safe fallbacks.

The complete directive reference and examples are in
[Fonts, symbols, shaping, and metrics](Terminal_Modernization.md#fonts-symbols-shaping-and-metrics).

## Reload and troubleshoot

After editing files manually, run:

```sh
termux-reload-settings
```

If the selected family appears ignored:

1. check whether `fonts.conf` overrides the managed fragment;
2. inspect `~/.termux/fonts.d/10-launcher.conf` and the font files it names;
3. look for the terminal's font-configuration notice; and
4. temporarily rename `fonts.conf` to test the lower layers.

Per-pane pinch zoom changes text size, not the selected family. See
[Resize panes and text](Launcher_Usage.md#resize-panes-and-text).
