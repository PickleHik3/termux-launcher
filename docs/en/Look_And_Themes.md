# Look and themes

This page covers the wallpaper, the glass look of the bars, colours, icons and the terminal's cursor
and screen effects. The status bar, the dock and the keyboard look the same on every page.

## Appearance

Open **Appearance** from Settings, from **Appearance** on a corner tab, from the terminal's
long-press menu or from the command palette. You see your launcher, live, in a frame, and the
editor bar at the bottom: the orientation chip, **Undo**, **Done** and the palette.

The rule is one: **tap the thing**.

- **Tap a surface** in the frame (Home, the status bar, the terminal, the dock, the keyboard) and
  its card rises with everything about it: how it looks, how big it is, where it sits, whether it
  shows. The surface you are tuning wears a ring; the others a faint outline.
- **Tap the palette** for what has no single surface: the look every surface shares, the
  wallpaper, the theme and the icons.
- **Swipe across the frame** to slide between Home and the Terminal.
- **Hold a surface and drag it** to another edge.

There are six cards: **Global**, **Home**, **Terminal**, **Status bar**, **Dock** and **Keyboard**.
A card opens halfway, with the controls you use most; drag its handle up, or open **More**, for
the rest. A tap on the frame or Back puts it down.

Everything previews as you go. **Done** keeps it all, **Undo** puts everything back to how it was
when you opened Appearance, and Back or Home with a change asks whether to keep editing, discard
or save.

A surface you hide (its **Visible** switch off) keeps a dashed outline in the frame, so you can tap
it and show it again. The orientation chip shows the other orientation as a drawing you can arrange;
those changes appear when you turn the phone.

## Global

Global is the look every surface wears. Change it once and the status bar, the dock, the keyboard,
menus and widgets on glass all follow.

- **Look**: **Presets** (**Clear**, **Frosted**, **Tinted**, **Solid**, **Liquid** and **Custom**;
  a new install starts on **Frosted**), **Opacity**, **Frost** (clear to frosted, in eight steps)
  and **Tint**, with the accent it takes.
- **Fine-tune** (closed until you open it): **Depth** and **Shine**, the two glass sliders that
  apply to every surface (see [Glass](#glass)); with **Glass refraction** off they have no effect.
- **Shape**: **Style** (Docked or Floating), **Corner radius**, **Margin** and **Icon size**, one
  size for Home's icons and the dock's.
- **Colours**: **Theme** (**System**, **Light** or **Dark**), **Contrast**, **Accent**,
  **Wallpaper colours** and **Tool colours** (see
  [Tools that follow the terminal colours](#tools-that-follow-the-terminal-colours)).
- **Screen**: **Show wallpaper** and **Change**, which opens the wallpaper picker over the editor
  (below), **Full screen**, **Text size** and **Page transition**.

**Text size** makes the terminal text, the keyboard's letters and the labels on the status bar, the
dock and Home larger or smaller together. Each keeps its own size on top of it: a pinch still zooms
a terminal pane, and Home's label size still applies.

Moving a slider, or picking a preset, changes Global only. A surface that has its own value keeps
it.

## Giving a surface its own look

The status bar, the dock and the keyboard may each have their own **Opacity** and **Blur**, under
**Appearance** on their cards. Nothing else about the glass is theirs to change; the rest is
Global's.

- Moving a surface's **Opacity** or **Blur** slider makes that value its own. A surface with its
  own **Blur** wears that blur's grain with it.
- A reset arrow (↺) at the end of the row marks a value that is the surface's own. Tap it to put
  that value back on Global.
- The same arrow in the card's header puts the whole surface back on Global. On the Global card it
  appears while any surface has a value of its own, and asks once before putting every surface back.

Menus and dialogs wear Global, never less opaque than keeps their text readable. Widgets wear
Global too, if **Glass widgets** on the Home card is on; a widget can still choose its own look
from its own settings.

## The Terminal's Background

The **Terminal** card holds the terminal's own background. It does not follow Global and has no
reset arrow.

- **Opacity**: how solid the panes are. A new install ships it higher than Global's glass, so the
  terminal reads darker than the bars.
- **Blur**: how much the panes blur what is behind them.
- **Wallpaper dim**: how much the wallpaper is darkened on the Terminal's pages, the Display and
  Assistant pages included. Home's pages are not dimmed.

## The other cards

- **Home**: **Columns**, **Rows**, **Label size**, **Icon pack**; under More, **Two-line labels**,
  **Label shadow**, **Page dots**, **Folders** and **Wallpaper scrolling**; **Glass widgets** and
  **Numerals**; the rows Home shows; and links to Home's gestures and pages.
- **Terminal**: **Font size**, **Contrast** and a link to its font; the Background above; **Pane
  borders**, **Split gap**, **Multiplexer**, **Automatic tiling** and **Focus grows**; **Cursor
  trail** and **Screen effect**, with **Screen events** and **Composite signal** while an effect is
  on; the rows the Terminal shows and **Minimal layout**.
- **Status bar**: its place and its indicators (CPU, memory, battery, media and weather);
  **Clock face**, **Clock position**, **Show seconds**, **12 h / 24 h** and **°C / °F** (see
  [Status bar](Status_Bar.md#clock)); **Opacity** and **Blur**; links to the camera cutout and
  pinned notifications.
- **Dock**: **Type** and **Apps in the dock**; under More, **Dock icon pack**,
  what the dock shows and **Monochrome icons**; its bars; **Opacity** and **Blur**.
- **Keyboard**: **Type** (Docked, Floating or Split), **Key spacing**, **Key corners**
  and **Bottom padding**; while it is Floating or Split, **Width**, **Floating height** and **Split
  gap**; **Opacity**, **Key opacity** and **Blur**; links to its theme and layouts (see
  [Keyboard](Keyboard.md)).

Heights, places and Type are one for Home and the Terminal, so nothing moves when you swipe.

## Wallpaper

**Change**, beside **Show wallpaper** on the Global card, opens the picker over the editor. Back returns to the editor;
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

Three sliders set the glass. **Frost** is how rough the face is: clear glass at one end, ground
glass at the other; Opacity and Frost can differ per surface. **Depth** is how thick the glass
is, so how strongly it bends the wallpaper at its edge; the far end is a thick lens. **Shine** is
how much of the wallpaper's light shows in the glass: bright, colourful parts of the wallpaper
next to a surface spill their colour into its edge; the far end throws a little colour. Depth and Shine
are the same on every surface. The presets set them: **Clear** is clear glass with a visible
edge, **Frosted** a blurred face with the same edge, **Tinted** coloured glass, **Solid** near
opaque for easy reading, and **Liquid** a thick, bright, dramatic lens. An existing look keeps
its feel when you update: your blur, depth, rim and edge settings map to the nearest Frost,
Depth and Shine, and the old Clear, Mist, Tint and Solid become Clear, Frosted, Tinted and Solid.

Every glass surface bends the wallpaper at its edge, as deeply as Global's Depth says, where the
phone can. That needs Android 13 and a wallpaper set through Appearance or
`launcherctl wallpaper set`; with any other wallpaper the glass stays flat. The glass has no
animation of its own: its motion follows **Animation speed**, and reduced motion, battery saver and
**Reduce idle activity** keep it still.

On a weaker phone, turn **Glass refraction** off under **Settings → Battery & performance**. The
glass becomes flat frosted glass, with no bending and no light from the wallpaper, and swipes get
smoother. Frost and Opacity still apply. Why glass works this way:
[ADR 0033](../adr/0033-glass-is-a-physical-material.md).

The bars blur the system wallpaper only after you allow **Wallpaper access** under **Settings →
General → Permissions & services**; until then they render flat.

## Cursor trail and terminal effect

Both are on the Terminal card:

- **Cursor trail**: **Off**, **Default**, **Motion blur**, **Railgun**, **Torpedo**, **Pixie dust**,
  **Comet**.
- **Terminal effect**: **None**, **CRT**, **CRT (green)**, **CRT (amber)**, **TFT grid**. It covers
  the status bar, dock and keyboard too, with scanlines fixed to the screen.
- **Composite signal** (CRT panes only, off by default): blends neighbouring pixels sideways so
  dithering and pixel art read as shades; text gets a little softer.

## Pane borders

**Pane borders** on the Terminal card opens its own card; the back arrow in its header, or Back,
returns to the Terminal card. Three small frames at the top show a pane as it rests, as the focused
pane of a split, and asking for attention (which always wears the error colour). Below them:

- **Border colour**: **Theme outline**, **Primary**, **Secondary**, **Tertiary** or **Custom**.
  Custom opens a **Custom colour** field that takes `#RRGGBB`, or `#AARRGGBB` for a see-through
  colour.
- **When focused**: the colour of the focused pane in a split and of floating panes, the border's
  own (**Same as border**) or any of the choices above. A single pane never takes it.
- **Glow**: **Off** or **Steady**, a soft glow round every pane, and **Glow colour** for it.
- **Reset pane borders** puts back the theme outline, Primary when focused and no glow.

## Icon pack

**Icon pack** on the Home card opens the installed packs under it, previewed on your real
dock. Tap a tile to apply it at once; the first tile, **System**, uses the apps' own icons.
**Pinned app icons only** limits the pack to the pinned apps.

## Appearance in Settings

How a thing looks and where it sits is set in the Appearance editor; what the launcher does is set
in Settings. The **Appearance** row at the root of Settings, and the **Appearance** row at the top
of Home, Terminal and Keyboard, open the editor on the right card: the root row opens **Global**.
The command palette's **Look and feel settings** opens it too, and Settings search finds a row
that moved into the editor and opens its card.

These live in the editor, not in Settings:

- On **Global**: theme, wallpaper colours, contrast, wallpaper, full screen and text size.
- On **Home**: icon and label size, two-line labels, label shadow, folders, numerals, page dots
  and wallpaper scrolling.
- On **Status bar**: the 12 h / 24 h clock, °C / °F and the switches for the readings.
- On **Terminal**: font size, cursor trail, screen effect, screen events, composite signal,
  split gap, the multiplexer, automatic tiling and focus grows.
- On **Keyboard**: the floating width and height and the split gap.

Two pages stay pages, with a link from their card: **Terminal fonts**, see
[Terminal fonts](Terminal_Fonts.md), and **Tool colours** under **Settings → Terminal**, see
[Tools that follow the terminal colours](#tools-that-follow-the-terminal-colours).

**Contrast** on Global and on the Terminal card is **Softer · pastel**, **Default · system** or
**Harder · punchy**. It sets how strongly the terminal's text stands out: Harder gives brighter
text, a deeper background and richer colours, Softer the gentlest. It changes the terminal's
colours only, never the glass behind them. On a see-through terminal it is judged against the
wallpaper you can actually see; over a wallpaper too bright for light text, the terminal switches
to dark text and tells your programs it is in light mode. **Wallpaper colours** matches the
launcher and terminal to the wallpaper, and overrides manual terminal colours; turn it off, or
apply a Termux:Styling scheme, to use your own.

## Tools that follow the terminal colours

Pick tools in this list and the launcher writes their theme file whenever the palette changes, then
wires it into the tool's own config. Turning one off puts its config back. Built in: Starship,
Helix, tmux, bat, Yazi, fzf, lazygit, Neovim and herdr.

Each tool's config stays yours apart from the lines the launcher writes, which sit between
`# >>> launcher-material >>>` and `# <<< launcher-material <<<` markers. For herdr that is
`auto_switch` in `[theme]` and the `[theme.custom.dark]` and `[theme.custom.light]` palettes; a
line of your own with one of those names is commented out with a `# launcher-material:` prefix
and put back when you turn the tool off. The sidebar rows in `[ui.sidebar.spaces]`,
`[ui.sidebar.agents]` and `[ui.sidebar.agents.rows_by_agent]` are only defaults: write your own
row there and the launcher keeps it, content and colours, through every re-theme.

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
