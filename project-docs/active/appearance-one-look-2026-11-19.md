# Appearance: one look for the chrome, six cards, one home per setting (2026-11-19)

Settled with the developer on 2026-11-19: a review of the Appearance settings, then a grilling
round (AskUserQuestion). The model is [ADR 0031](../../docs/adr/0031-the-chrome-wears-one-look-a-page-carries-what-differs.md).
Status: specified; nothing built. Tickets follow.

**Supersedes** in `appearance-cards-overhaul-2026-11-02.md`: §1 (the three main cards, the
Applies to row, the precedence, "a Look resets beneath it", what each card writes) and the card
lists of §2. Its §3 (collapsible sections), §4 (controls) and §5 (Apps in the dock) still hold.
**Supersedes** `surface-look-grid-2026-10-30.md` where it gives a value a page or kind.

## 1. Rules

1. **The chrome wears one look everywhere.** The status bar, the dock and the keyboard look the
   same on every page. No card chooses a page; there is no Applies to row.
2. **Two places.** A glass value is Global's, or one surface's own. A surface may own Opacity and
   Blur only.
3. **Own stays.** A change on Global, a preset included, never clears a surface's own value.
4. **A page carries what differs.** The Terminal has its own Background; Home has its grid and
   widgets. Each page card lists the rows that page shows.
5. **One home per setting.** How a thing looks and where it sits lives in the editor; what the app
   does lives in Settings. A Settings page keeps one Appearance row that opens the right card. A
   full-page editor (fonts, keyboard theme, keyboard layouts, extra keys, camera cutout, icon
   packs) stays a page, with a link from its card. The Performance page is the one allowed set of
   copies. The setup wizard may set anything once and is not a home.
6. **Words.** Global (was Base) and Terminal (was Workspace), on screen, in code and in docs.

## 2. The reset arrow

An own value is marked by a reset arrow (↺), never by text or a coloured dot.

- **On a row:** every row keeps a fixed slot at its end. The arrow shows there while the value is
  the surface's own and the slot is empty otherwise, so every slider is the same length and the
  glyphs stand in one column. A tap puts that value back on Global.
- **In a surface card's header:** shown while the surface owns anything; a tap puts the whole
  surface back on Global.
- **In the Global card's header:** shown while any surface owns anything; a tap asks once ("Put
  every surface back on Global?") and then clears every own value.

Moving a surface's Opacity or Blur slider is what makes the value its own. There is no
"Follows Global" line and no "Give it its own" button.

## 3. The cards

Section names and controls as in the 11-02 spec §3 and §4. Rows in order.

### Global
- **Look**: Presets, Opacity, Blur, Tint (strength and accent swatches).
- **Fine-tune** (closed by default): Grain, Rim strength, Edge, Depth, Vibrancy.
- **Shape**: Style (Docked · Floating), Corner radius, Margin, **Icon size** (S · M · L · XL; from
  the Home and Dock cards: one value sizes Home's icons and the dock's).
- **Colours**: Mode, Contrast, Accent, Dynamic colour, TUI app themes ↗.
- **Screen**: Show wallpaper, Full screen, **Text size** (80–150%; from the Terminal card, where
  it was named Font size), **Page transition** (from the Home card).
- Gone from here: Status position (the Status bar card has it), Dock type (the Dock card has it),
  the Layout list and its "has its own layout" dialog, the Menus and dialogs link.

### Home (hidden under Use as: Terminal only)
- **Home**: Columns, Rows, Label size, Icon pack.
- **More options**: Two-line labels, Label shadow, Page dots, Folders, Wallpaper scrolling.
- **Widgets**: Glass widgets, Numerals font.
- **Layout**: the rows Home shows.
- **Links**: Gestures ↗, Pages ›.
- Gone: the ten glass rows, Wallpaper dim, Transition, Icon size, the Global and Menus links.

### Terminal (Workspace and Terminal merged)
- **Text**: **Font size** (the terminal's own, from the Settings fonts page), Contrast, Font ↗.
- **Background**: Opacity (the panes), Blur, **Wallpaper dim** (everything else on the Terminal's
  pages; from the Home card). No reset arrow: these never follow Global.
- **Panes**: Pane borders ›, Inner gap, Multiplexer, Automatic tiling, Focus grows.
- **Effects**: Cursor trail, Screen effect; while Screen effect is on, Screen events and Composite
  signal (from Settings › Terminal › Text and display).
- **Layout**: the rows the Terminal shows, Minimal layout.
- **Links**: Pages › (while Home is hidden).
- Gone: Presets, Grain, Tint, Accent, Corner radius, Rim strength, Edge and Depth for the
  terminal; the second Contrast row and the second Layout list (they were the Workspace card's).

### Status bar
- **Status bar**: Position, Elements.
- **Clock & Weather**: Clock face, Clock position, Show seconds, 12 h / 24 h, °C / °F.
- **Appearance**: Opacity, Blur (own or following Global).
- **Links**: Camera cutout ↗, Pinned notifications ↗.

### Dock
- **Dock**: Type, Apps in the dock; Strip height while Type is Strip.
- **More options**: Dock icon pack, Show/hide (Search field, Drawer icon, Command palette, Float
  terminal), Monochrome icons.
- **Bars**: order and each bar's own settings. Whether a bar shows is set on the page cards.
- **Appearance**: Opacity, Blur (own or following Global); Reflection while Type is Peek.
- Unchanged under Terminal only: the apps row, drawer icon and search field belong to the
  Terminal's layout and keep working there.

### Keyboard
- **Keyboard**: Default type, Height, Key spacing, Key corner radius, Bottom padding; while the
  type is Floating or Split: Width, Floating height, Split gap (from Settings › Keyboard › Size
  and position).
- **Appearance**: Opacity, the Linked toggle, Keys opacity, Blur (own or following Global).
- **Links**: Keyboard theme ↗, Keyboard layouts ↗.

### No card
- **Widgets**: Home › Glass widgets decides whether widgets wear Global's glass; a widget's own
  choice stays in its settings.
- **Menus and dialogs**: wear Global, with a floor on opacity set in code.
- **Display and Assistant pages**: use the Terminal's Layout and Wallpaper dim.

## 4. Settings

Rows that leave a Settings page because the editor is their home:

| Page | Rows that go |
|---|---|
| The old Look page (`termux_style_preferences.xml`) | All of it: App theme, Wallpaper colors, Terminal contrast, Wallpaper, Widgets on glass, Monochrome icons, Icon pack, Pinned-app icon pack, Full screen. TUI app themes becomes its own page under Terminal. |
| Home | Icon size, Label size, Two-line labels, Label shadow, Folder density, Widget numerals, Page indicator, Wallpaper scrolling |
| Status bar | 12-hour time; the CPU, memory, battery, media and weather switches; Fahrenheit |
| Terminal › Text and display | Cursor trail, Terminal effect, Screen events, Composite signal |
| Terminal › Windows and panes | Multiplexer (Split-pane controls), Automatic tiling, Focus active pane |
| Terminal fonts | Font size |
| Keyboard › Size and position | Floating width, Floating height, Split gap |

Stays as it is: the Performance page and its copies; every full-page editor; General's Page
transition row (already a door); Use as, Start in and Animation speed.

Also: the root row **Look** is renamed **Appearance** and opens the Global card; the command
palette's Look entry opens the editor; a search hit for a row that moved opens the editor on its
card (`appearance_search_index.xml` grows to cover them).

## 5. Shipped values and upgrading

- The Terminal ships with a higher Opacity than Global's glass. The number is picked on pong.
- Nothing is converted. The build that carries this raises `AppearanceReset.NUMBER`; the per-kind
  and per-page look keys and the per-page wallpaper dim keys are deleted by that reset.

## 6. Order of work

1. **The cut.** The look grid answers one value per chrome surface whatever the page, and the
   editor stops writing kind cells. Look-blend polishing on `fix/wr-peek-blend` pauses until this
   lands; the Peek dock and keyboard up/down fixes carry on.
2. **Delete the look blends** the cut made unreachable.
3. **Prototype: glass shows the ground beneath it** (ADR 0031 §5), judged on pong.
4. **The editor**: six cards, the reset arrow, the moved rows.
5. **The Terminal's Background**: Wallpaper dim on the Terminal card, Home's dim gone, the darker
   shipped Opacity.
6. **Settings**: the rows of §4, the Look page retired, the doors.
7. **Words and docs**: Base to Global, Workspace to Terminal; `launcherctl look` without `--kind`
   and `--page`; the user guides.

## 7. Settled since

- A surface holding its own Blur wears that stop's grain; following Global's Blur, it wears
  Global's Grain (developer, 2026-11-19).
- Nothing ships per surface: a fresh install's keyboard wears Global's glass, as the dock and
  the status bar do; the keyboard's Mist on Home and Clear on Display are gone (developer,
  2026-11-19).
- The Home float wears Global's glass like everything else on Home, with no opacity floor
  (developer, 2026-11-19).

## 8. Still open

- The floor on opacity for Menus and dialogs: the number.
- The Terminal's shipped Opacity: the number.
- Whether the prototype of step 3 holds its frame budget.
- With "own stays", whether the Global card's Presets row reads Custom while a surface owns a
  value, or reflects Global's values alone.
