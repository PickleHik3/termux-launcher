# Look and themes

This page covers the wallpaper, the glass look of the bars, colours, icons and the terminal's cursor
and screen effects. Home and the Workspace each wear their own look, equal until you change one.

## Appearance

Open **Appearance** from Settings, from **Appearance** on a corner tab, from the terminal's
long-press menu or from the command palette. You see your launcher, live, in a frame, and the
editor bar at the bottom: the orientation chip, **Undo**, **Done** and the palette.

The rule is one: **tap the thing**.

- **Tap a surface** in the frame (Home, the status bar, the terminal, the dock, the keyboard) and
  its card rises with everything about it: how it looks, how big it is, where it sits, whether it
  shows. The surface you are tuning wears a ring; the others a faint outline.
- **Tap the palette** for what has no single surface: the Look, the wallpaper, the theme, icons,
  text size, full screen and minimal mode.
- **Swipe across the frame** to slide between Home and the Workspace. The dots and the name under
  the frame say which one you are changing; a card changes that one.
- **Hold a surface and drag it** to another edge.

A card opens halfway, with the controls you use most; drag its handle up, or open **More**, for
the rest. ✕, a tap on the frame or Back puts it down. Its ⋮ menu has **Reset** (the surface back on
the Look) and, for the dock, the status bar, the keyboard and the Look, **Copy to Home** or
**Copy to Workspace**, which makes the other half look the same.

A slider you move is that surface's own value, marked by a small dot after its name; Reset takes it
back. Moving one while a Look is chosen turns the Look to **Custom** by itself.

Everything previews as you go. **Done** keeps it all, **Undo** puts everything back to how it was
when you opened Appearance, and Back or Home with a change asks whether to keep editing, discard
or save.

A surface you hide (its **Visible** switch off) keeps a dashed outline in the frame, so you can tap
it and show it again. The orientation chip shows the other orientation as a drawing you can arrange;
those changes appear when you turn the phone.

## The Appearance card

- **Look**: a slider with five stops, **Clear**, **Mist**, **Tint**, **Solid** and **Custom**, for
  the half the frame shows. Clear is the most see-through and Solid the least. Custom brings back the
  look you made yourself.
- **Wallpaper**: shown behind the launcher or not, and **Change**, which opens the wallpaper picker
  over the editor (below).
- **Theme**: **System**, **Light** or **Dark**.
- **Icon pack**, **Monochrome**, **Text size**, **Wallpaper colours**, **Contrast**, **Full screen**
  and **Minimal**.
- Under **More**: **Alignment** (lines the wallpaper behind the surfaces up with the one on your
  screen), **Vibrancy**, **Tint colour**, **Depth** and **Glass edge** where the glass edge is drawn
  (see [Glass](#glass)), and **Tool colours** (see
  [Tools that follow the terminal colours](#tools-that-follow-the-terminal-colours)).

**Text size** makes the terminal text, the keyboard's letters and the labels on the status bar, the
dock and Home larger or smaller together. Each keeps its own size on top of it: a pinch still zooms
a terminal pane, and Home's label size still applies.

## The surfaces' cards

- **Home**: **Icon size**, **Label size** and **Glass widgets**; **Two-line labels**,
  **Label shadow**, **Folders**, **Numerals** and **Page dots**; under More, **Folder limit**,
  **Wallpaper scroll**, **Own layout** (Home laid out unlike the Workspace) and links to Home's
  gestures and pages. A widget's own choice from its long press still wins over Glass widgets.
- **Status bar**: **Opacity**, **Tint**, **Visible** and **Position**; **Blur**, **Corners**,
  **Margin**, **12-hour clock** and **Clock face** (see [Status bar](Status_Bar.md#clock)); under
  More, **Grain**, **Depth** and a link to its indicators.
- **Terminal**: **Opacity**, **Tint** and **Text size**; **Blur**, **Corners**, **Margin** and
  **Contrast**; under More, **Grain**, **Cursor trail**, **Effect**, **Edge colours**, **Split gap**
  and a link to its font.
- **Dock**: **Opacity**, **Tint**, **Height**, **Visible** and **Position**; **Style** (Docked or
  Floating), **Design** (Tray, Beads or Strip), **Blur**, **Corners** and **Margin**; under More,
  **Grain**, **Depth**, **Search field**, **App count** and a link to its items.
- **Keyboard**: **Opacity**, **Tint**, **Height** and **Visible**; **Keyboard theme** (its colours
  and font, see [Keyboard](Keyboard.md)), **Blur**, **Corners** and **Margin**; under More,
  **Grain**, **Key opacity**, **Key corners**, **Key spacing**, **Type** (Docked, Floating or Split)
  and links to its size and layouts.

The glass of the dock, the status bar and the keyboard (opacity, tint, blur and grain) and the Look
are held per half. Corners, margin, heights, places, Style and Design are one for both halves, so
nothing moves when you swipe.

## Wallpaper

**Change** on the Appearance card opens the picker over the editor. Back returns to the editor;
**Done** keeps the wallpaper and everything else you changed and closes Appearance.

- The picker has a **Lock screen** card and a **Home screen** card. Tap a card, then **Choose photo**
  to pick an image. It is previewed before anything is applied.
- While your current wallpaper was set by another app, a small grey line under **Choose photo**
  reads "Set your wallpaper here to see fancier glass." It goes away once you apply a wallpaper from
  Appearance.
- Recent photos stay in a strip for quick reuse.
- Set Lock to **Same as Home** to have it follow Home.
- If another app set your lock-screen wallpaper, applying Home leaves it alone.

**Done** puts Home's picture on Home and Lock's on Lock. Scripts can do the same with
`launcherctl wallpaper set FILE --home|--lock|--both`; see [launcherctl](LauncherCtl.md).

### Glass

Every glass surface bends the wallpaper at its edge, as deeply as the chosen Look says, where the
phone can. That needs Android 13 and a wallpaper set through Appearance or
`launcherctl wallpaper set`; with any other wallpaper the glass stays flat. **Reduce idle activity**,
battery saver and reduced motion switch the extra motion off. There is no switch for it.

The bars blur the system wallpaper only after you allow **Wallpaper access** under **Settings →
General → Permissions & services**; until then they render flat.

## Cursor trail and terminal effect

Both are on the Terminal card under More, and under **Settings → Terminal → Text & display**:

- **Cursor trail**: **Off**, **Default**, **Motion blur**, **Railgun**, **Torpedo**, **Pixie dust**,
  **Comet**.
- **Terminal effect**: **None**, **CRT**, **CRT (green)**, **CRT (amber)**, **TFT grid**. It covers
  the status bar, dock and keyboard too, with scanlines fixed to the screen.

## Icon pack

**Icon pack** on the Appearance card opens the installed packs under it, previewed on your real
dock. Tap a tile to apply it at once; the first tile, **System**, uses the apps' own icons.
**Pinned app icons only** limits the pack to the pinned apps.

## Appearance settings

**Settings → Look** is a settings page; the command palette's **Look and feel settings** opens
it too, and Settings search finds every row on it. Its first row opens the Appearance editor
above; the rows below it write the same settings as the Appearance card. Below that it holds:

- **Terminal fonts**: see [Terminal fonts](Terminal_Fonts.md).
- **App theme**: **System**, **Light** or **Dark**.
- **Wallpaper colors**: "Match the launcher and terminal to the wallpaper." On by default. It
  overrides manual terminal colours; turn it off, or apply a Termux:Styling scheme, to use your own.
- **Terminal contrast**: **Softer · pastel**, **Default · system** or **Harder · punchy**. It sets
  how strongly the terminal's text stands out: Harder gives brighter text, a deeper background and
  richer colours, Softer the gentlest. It changes the terminal's colours only, never the glass
  behind them. On a see-through terminal it is judged against the wallpaper you can actually see;
  over a wallpaper too bright for light text, the terminal switches to dark text and tells your
  programs it is in light mode. It reads "Available when wallpaper colors are on" otherwise.
- **Tools that follow the terminal colours** (below).
- **Wallpaper**: show the system wallpaper behind launcher surfaces.
- **Wallpaper parallax**: the wallpaper pans a little as the row slides, from Home page 1 across
  the windows to the last page. Needs a wallpaper set through the launcher's picker; off in
  landscape and with animations off.
- **Wallpaper alignment**: lines up the wallpaper behind the surfaces with the one on your screen.
- **Monochrome icons**, **Icon pack** and **Pinned-app icon pack**.
- **Keyboard look**: **Appearance** (opens the Keyboard card) and **Keyboard theme**.

## Tools that follow the terminal colours

Pick tools in this list and the launcher writes their theme file whenever the palette changes, then
wires it into the tool's own config. Turning one off puts its config back. Built in: Starship,
Helix, tmux, bat, Yazi, fzf, lazygit, Neovim and herdr.

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

**Settings → General → Reduce idle activity** ("Lazy mode. Pause idle animations and update
status less often.") is off by default. With it on, the clock swaps its digits instead of folding
them, a working window's rim holds lit instead of breathing, the readings sample less often, and
the weather icon rests on its last frame. Nothing repaints until something changes, which saves
battery.
