# Look and themes

This page covers the wallpaper, the glass look of the bars, colours, icons and the terminal's cursor
and screen effects. Home, Terminal and Display share one look, so a change applies to all three.

## Appearance

Open **Appearance** from **Settings → Appearance**, from **Appearance** on a corner tab, or from the
terminal's long-press menu. It has four tabs in a pill at the top: **Wallpaper | Look | Layout |
Icon pack**. It opens on Wallpaper, or on the tab you last used if you were there in the last half
hour.

**Done**, top right on every tab, applies everything you changed on any tab and closes. Back or
Home with a wallpaper not yet applied asks whether to keep editing, discard or save. The Layout tab
is described on [Layout and full screen](Layout_And_Full_Screen.md).

**On a tablet in landscape** (smallest width 600dp or more), the controls sit in a side pane on the
right instead of a sheet at the bottom, and the preview fits and centres to its left. The
**Wallpaper | Look | Layout | Icon pack** pill stays on top, tall pages scroll inside the pane, and
the clock face popup opens inside it. Turn the tablet to portrait for the bottom sheet. Phones keep
the bottom sheet in both orientations.

## Wallpaper

The Wallpaper tab has a **Lock screen** card and a **Home screen** card.

- Tap a card, then **Choose photo** to pick an image. It is previewed before anything is applied.
- While your current wallpaper was set by another app, a small grey line under **Choose photo**
  reads "Set your wallpaper here to see fancier glass." It goes away once you apply a wallpaper from
  Appearance.
- Recent photos stay in a strip for quick reuse.
- Set Lock to **Same as Home** to have it follow Home.
- If another app set your lock-screen wallpaper, applying Home leaves it alone.

**Done** puts Home's picture on Home and Lock's on Lock. Scripts can do the same with
`launcherctl wallpaper set FILE --home|--lock|--both`; see [launcherctl](LauncherCtl.md).

## Look

The Look tab has a slider with five stops: **Clear**, **Mist**, **Tint**, **Solid** and
**Custom**. Clear is the most see-through and Solid the least.

At **Custom**, vertical sliders appear:

- With nothing tapped: **Blur**, **Grain**, **Opacity**, **Tint**, **Margin** and **Corners**, for
  every surface.
- Tap an element in the frame to tune it on its own:
  - **Status bar**: the four glass sliders, plus a **Clock** button for the face and alignment
    (see [Status bar](Status_Bar.md#clock)).
  - **Terminal**: the glass sliders and **Contrast**, plus **Cursor trail** and **Terminal effect**.
  - **Dock**: the glass sliders, **Size** and **Icons** (how many app buttons show).
  - **Keyboard**: the glass sliders, **Radius** and **Spacing**, plus a **Keyboard theme** button
    for its theme, colours and font (see [Keyboard](Keyboard.md)).
- Tap bare wallpaper to go back to the global sliders. **Undo** steps back.

### Glass

Every glass surface bends the wallpaper at its edge, as deeply as the chosen Look says, where the
phone can. That needs Android 13 and a wallpaper set through the Wallpaper tab or
`launcherctl wallpaper set`; with any other wallpaper the glass stays flat. **Reduce idle activity**,
battery saver and reduced motion switch the extra motion off. There is no switch for it.

The bars blur the system wallpaper only after you allow **Wallpaper access** under **Settings →
Permissions & services**; until then they render flat.

## Cursor trail and terminal effect

Both are on the Terminal element at Custom, and under **Settings → Terminal → Terminal display**:

- **Cursor trail**: **Default**, **Motion blur**, **Railgun**, **Torpedo**, **Pixie dust**,
  **Comet**.
- **Terminal effect**: **None**, **CRT**, **CRT (green)**, **CRT (amber)**, **TFT grid**. It covers
  the status bar, dock and keyboard too, with scanlines fixed to the screen.

## Icon pack

The Icon pack tab previews each installed pack on your real dock. Tap a tile to apply it at once;
the first tile, **System**, uses the apps' own icons. **Pinned app icons only** limits the pack to
the pinned apps.

## Theme & fonts

The **Theme & fonts** page has no row in Settings. Reach it by searching Settings (for example
`wallpaper colors` or `fonts`), or from the command palette's **Look and feel settings**. It holds:

- **Terminal fonts**: see [Terminal fonts](Terminal_Fonts.md).
- **App theme**: **System**, **Light** or **Dark**.
- **Wallpaper colors**: "Match the launcher and terminal to the wallpaper." On by default. It
  overrides manual terminal colours; turn it off, or apply a Termux:Styling scheme, to use your own.
- **Terminal contrast**: **Softer · pastel**, **Default · system** or **Harder · punchy**. It
  shapes the wallpaper palette, and reads "Available when wallpaper colors are on" otherwise.
- **Tools that follow the terminal colours** (below).
- **Wallpaper**: show the system wallpaper behind launcher surfaces.
- **Wallpaper parallax**: the wallpaper pans a little as the places slide. Needs a wallpaper set
  through the launcher's picker; off in landscape and with animations off.
- **Wallpaper alignment**: lines up the wallpaper behind the surfaces with the one on your screen.
- **Monochrome icons**, **Icon pack** and **Pinned-app icon pack**.
- **Keyboard look**: **Surface style** (opens Appearance) and **Keyboard theme**.

## Tools that follow the terminal colours

Pick tools in this list and the launcher writes their theme file whenever the palette changes, then
wires it into the tool's own config. Turning one off puts its config back. Built in: Starship,
Helix, tmux, bat, Yazi, fzf, lazygit, Oh My Posh, Neovim, fish and herdr.

To add your own, make a folder `~/.termux/theme-templates/<id>/` holding a `template.properties`
manifest, the file to render and its hooks:

```properties
name      = Starship
summary   = Prompt palette
input     = starship.toml
output    = ~/.config/launcher-material/starship.toml
post_hook = apply.sh
undo_hook = undo.sh
```

- Templates in that folder apply because they are there; an id that matches a shipped template
  replaces it. `output` understands `~`, `$VAR` and `${VAR}`.
- The input is plain text with `{{ colors.<token>.<mode>.<format> }}` placeholders, for example
  `{{ colors.primary.dark.hex }}`. Tokens are the keys in `~/.termux/material-colors.properties`;
  mode is `default`, `dark` or `light`; formats are `hex`, `hex_stripped`, `rgb`, `rgba`, `red`,
  `green` and `blue`. `{{ mode }}` renders `dark` or `light`. An unknown token or format skips the
  template and logs why.
- `post_hook` runs after the file is written, `undo_hook` when the template is turned off. Both run
  as `bash <hook>` from the template folder with `TERMUX_THEME_ID`, `TERMUX_THEME_DIR`,
  `TERMUX_THEME_OUTPUT` and `TERMUX_THEME_MODE` set, and are stopped after thirty seconds. Make them
  repeatable, and have the undo remove exactly what the apply added.
- An optional `setup_hook` is for a tool switched on by a line in your shell startup file. Turning
  the tool on offers you the command to run once; the launcher never edits that file itself.

The launcher also exports its palette for your own scripts to `~/.termux/material-colors.sh` and
`.properties`. A wallpaper palette adds `material-colors-dark.*` and `material-colors-light.*`.

## Theming from a color scheme

Turn **Wallpaper colors** off, or apply a Termux:Styling scheme, and the launcher's chrome (dock,
status bar, app drawer, keyboard, command palette) is built from `~/.termux/colors.properties`. This
needs Android 11 or newer; below that only the terminal follows the scheme.

The scheme is the anchor: `background` becomes the surface, `foreground` the text, `color4` (or a
distinct `cursor`) the accent, `color1` the error colour and `color8` the dividers. The remaining
tones are derived from the background, with contrast repaired afterwards.

Override any colour in `~/.termux/launcher-theme.properties`, one token per line:

```properties
primary                = color3
surface_container_high = lighten(surface, 0.08)
outline_variant        = mix(on_surface, surface, 0.78)
scrollbar              = alpha(on_surface_variant, 0.3)
```

A value is a hex colour, a scheme key (`background`, `foreground`, `cursor`, `color0` to `color15`),
another token, or `lighten`, `darken`, `mix` or `alpha` over those. Amounts take `0.25` or `25%`.
Lines that do not parse are ignored and logged. Single tokens in this file also apply on top of
wallpaper colours.

The tokens are `surface`, `surface_dim`, `surface_bright`, `surface_container_lowest`,
`surface_container_low`, `surface_container`, `surface_container_high`,
`surface_container_highest`, `on_surface`, `on_surface_variant`, `outline`, `outline_variant`,
`scrollbar`, `primary`, `on_primary`, `primary_container`, `on_primary_container`, the
`secondary`, `tertiary` and `error` families spelled the same way, and `inverse_surface`,
`inverse_on_surface` and `inverse_primary`.

## Reduce idle activity

**Settings → App behavior → Reduce idle activity** ("Lazy mode. Pause idle animations and update
status less often.") is off by default. With it on, the clock swaps its digits instead of folding
them, a working window's rim holds lit instead of breathing, the readings sample less often, and
the weather icon rests on its last frame. Nothing repaints until something changes, which saves
battery.
