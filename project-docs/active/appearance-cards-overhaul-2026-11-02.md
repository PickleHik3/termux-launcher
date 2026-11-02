# Appearance editor: card overhaul (2026-11-02)

Settled with the developer on 2026-11-02 in three grilled rounds (AskUserQuestion): the first
overhaul pass, the Material 3 Expressive control pass, and the restructure below, which
replaces the first two where they differ. The first-pass review page (before/after mockups,
now partly outdated) is https://amal-build.taild9c5dc.ts.net/reports/appearance-overhaul/, and
the 140 current-state shots (build 1d7506c51 on tl16) are at
https://amal-build.taild9c5dc.ts.net/reports/appearance-tour/. The mockups were not redrawn for
the restructure: the build goes straight from this file.

**Supersedes** in `appearance-editor-order-and-geometry-2026-11-01.md`: the section chips
(decision 1), the per-card orders, and the Display card (decision 4). That file's geometry
(decision 5: 0.84 / 0.50 / 0.28 scales, sheet chrome), fallbacks (6), drag (7) and gating (9)
still hold. One Reset per card (8) holds, and moves into the ⋮ menu.

## 1. Structure

- **Three main cards**: **Customization** (was Global, then Theme), **Home** and **Workspace**.
  Their top row is a three-part Material 3 Expressive connected pill
  *Customization | Home | Workspace*, followed by a small phone glyph at the row's end. The glyph
  is upright in portrait and sideways in landscape, and is an indicator only (not tappable).
- **Element cards**: Status bar, Dock, Terminal, Keyboard, Widgets, Menus and dialogs (was
  Sheets), and the existing sub-cards (Pane borders, Icon pack). They are reached from a
  **Layout** list or a link. Their top row is **← name · Show switch (where the element can be
  hidden) · ⋮**. ← returns to the card the user came from. The 3-part pill does not show on
  element cards.
- **⋮ menu**: *Reset <card> for <where>* (11-01 decision 8 wording). On Customization: *Reset
  everything* (the same reset the upgrade nudge offers, §6).
- **Applies to** row, second on the card: **Global · Home · Workspace**. Only on Status bar,
  Dock, Keyboard, and Menus and dialogs. Widgets (Home only) and Terminal (Workspace only) do
  not get it. *Workspace* means Terminal pages and Display pages together: a Workspace write
  goes to both the `terminal` and `display` page kinds. Existing Display-only values stay stored
  and keep applying until that setting is next changed from the editor.
- **Display card is removed**, and so is Workspace's *Terminal pages / Display pages* switch.
  Display pages follow Customization, then Workspace, then the element overrides.
- **Precedence (decided 11-02, W2a): element beats page.** The look grid reads
  element@kind → element (Global) → all-glass@kind (the Home / Workspace card) → Base
  (Customization) → shipped(kind). Before this, the page-wide cell was read before the element's
  Global value. A value set on an element card therefore wins over the Home or Workspace card on
  that page, and the page card wins over Customization.
- **What each card writes**: Customization → Base cells (Vibrancy and Margin are Base-only);
  Home → all-glass@kind:home; Workspace → all-glass@kind:terminal *and* @kind:display (reads
  show Terminal's); Widgets → widgets@kind:home; Terminal → terminal@kind:terminal and
  @kind:display; Status bar, Dock, Keyboard, Menus and dialogs → element (Global),
  element@kind:home (Home), or element@kind:terminal + @kind:display (Workspace). Presets on
  Home / Workspace / element cards write Frost, Opacity and Tint into those cells; on
  Customization they keep the global preset path.
- **Entry points, by place**:
  - Settings › Appearance and the peek-bar button open **Customization**.
  - Home long-press › Appearance opens **Home**.
  - The Terminal or Display pane corner › Appearance opens **Workspace**.
  - Long-press on the status bar, dock or keyboard › Edit opens that **element card**.
- **Peek bar**: Undo · Done · Customization. The Portrait/Landscape chip is removed and replaced
  by the header glyph above.
- **Wallpaper**: the *Change wallpaper* row leaves every editor card. The only place to change
  the wallpaper is Home long-press › Wallpaper. *Show wallpaper* stays on Customization.
  Settings pages are not touched.
- **No section chips.** Each section is a **collapsible card** inside the sheet (§3).

## 2. Sections and rows, card by card

Section names everywhere: **[card name] · More options · Layout · Appearance · Links**, in that
order, each only where the card has it (Customization adds *Colours*). The **Appearance** block
is the same on every card that has it: **Presets, Opacity, Blur, Grain, Tint (strength + accent
swatches), Corner radius, Rim strength, Edge, Depth**. *Vibrancy* is only on Customization
(it is drawn globally; per-element vibrancy is not in scope). Edge and Depth follow today's
capability gate; when a phone cannot draw them, they show disabled with "Not on this phone."

No item in a card's Links repeats an item in that card's Layout list.

### Customization
- **Appearance**: Presets, Opacity, Blur, Grain, Tint, Corner radius, Rim strength, Vibrancy,
  Edge, Depth. These are the defaults every surface wears unless a page or element overrides
  them (the grid's `Base` / all-element cells).
- **Colours**: Mode (System · Light · Dark), Contrast (Softer · Default · Harder), Accent
  (swatches), **Dynamic colour** (switch, was "Wallpaper colours / Match colours to wallpaper";
  the Accent swatches grey out while it is on).
- **Layout**: the full elements list, for the whole app. If Home or Workspace has its own layout
  that differs, changing an element here asks first:
  title "Home has its own layout" (or "Workspace…" / "Home and Workspace…"), one line naming
  what differs ("The A–Z row is hidden on Home."), buttons **Cancel · Keep Home's · Override**.
- **More options**: Full screen ("Hides Android's status and navigation bars."), Show wallpaper.
- **Links**: Menus and dialogs ›, TUI app themes ↗ (was "Tool colours"; opens the terminal tool
  colours setting in Settings).

### Home
- **Home** (open by default): Columns, Rows, Icon pack (the global icon pack), Icon size, Label
  size, Page transition.
- **More options**: Two-line labels, Label shadow, Page dots, Wallpaper scrolling (was
  "Wallpaper scroll"), Folders, Numerals font.
- **Layout** (was "Home layout" / Elements): every element row as today.
- **Appearance**: the block, overriding Customization for Home pages (`@kind:home` cells).
- **Links**: Customization ›, Gestures ↗, Pages ›, Menus and dialogs ›. The Widgets link goes,
  because Widgets is in Layout.

### Workspace
Header icon: a window outline with **>_** inside (new glyph), distinct from the Terminal card's
bare >_.
- **Appearance** (open by default): the block, overriding Customization for Terminal and Display
  pages. The **Minimal layout** switch follows in the same section, with no heading of its own
  ("A second layout with the bars and keyboard put away."). Minimal is not on Customization.
- **Layout** (was "Terminal layout"): every element row as today.

### Status bar
- **Status bar** (open by default): Position (Top · Bottom), Margin, **Elements** (was
  "Indicators"; toggle buttons): **Clock** (new setting: show or hide the clock), CPU, RAM,
  Weather, Notifications, Media.
- **Clock & Weather**: Clock face (picture grid), Clock position (Left · Centre · Right), Show
  seconds, 12 h / 24 h, °C / °F. While Clock is off, the clock rows are disabled with
  "Clock is hidden"; °C / °F stays live while Weather is on.
- **Appearance**: the block.
- **Links**: Camera cutout ↗, Pinned notifications ↗ (the Settings page's own title).

### Dock
- **Dock** (open by default): Type (four picture buttons in one row, no label row above them),
  Style (Docked · Floating), Margin, Icon size, **Apps in the dock**: a − n + stepper (§5).
  Position stays per bar, inside each bar's own settings, opened from Layout.
- **More options**: Dock icon pack (the pinned-app pack: "Same as Home" or a pack), Show/hide
  (was "Buttons"; toggle buttons): Command palette, Drawer icon, Notification dots, Quick reply,
  Search bar (was "Search field"); Monochrome icons (switch).
- **Layout**: the bars (Apps row, A–Z), drag handle to reorder, switch, › to the bar's settings.
- **Appearance**: the block (+ Reflection when Type is Peek, as today).
- **Links**: Menus and dialogs ›, Customization ›.

### Terminal
- **Terminal** (open by default): Font size (was Text size), Contrast, Pane borders ›, Cursor
  trail (picture grid), Screen effect (picture grid), Inner gap (was Split gap), Outer margin
  (was Margin), **Multiplexer**, **Auto tiling**, **Enlarge focused pane** (switches; today
  they are only in the setup wizard: Multiplexer = not compatibility mode, Auto tiling =
  `isDwindleDefaultLayoutEnabled`, Enlarge focused pane = the wizard's "Focused pane grows").
  Auto tiling and Enlarge focused pane are disabled while Multiplexer is off.
- **Layout**: as today.
- **Appearance**: the block. No Style row (the terminal has no Docked/Floating).
- **Links**: Font ↗.
- The setup wizard's three rows are reworded to match these names in intent (they need not be
  identical).

### Keyboard
- **Keyboard** (open by default): Default type (was Type: Docked · Floating · Split), Height,
  Key spacing, Outer margin (was Margin), Key corner radius (was Key corners), Bottom padding.
- **Appearance**: Presets, **Keyboard opacity** (was Background opacity), a centred
  half-width tonal toggle button **🔗 Linked / Not linked** (replaces the chain glyph and the
  "Move both opacities together" switch; filled when linked), **Keys opacity** (was Key
  opacity), then Blur, Grain, Tint, Corner radius (the keyboard's container), Rim strength,
  Edge, Depth.
- **Links**: Keyboard theme ↗, Keyboard layouts ↗.

### Widgets; Menus and dialogs
- **Appearance** only (the block). Widgets keeps its note: "A widget's own Glass setting
  overrides this." Menus and dialogs has Applies to.

## 3. Collapsible sections

Each section is a card inside the sheet: surface-container-lowest, 20 dp corners, 2 dp apart
(more between cards when collapsed is not needed). The section's title row (48 dp, title in
primary, an expand arrow that turns 180°) opens and closes it, and the whole title row is the
touch target. Any number can be open at once. Each card remembers which sections were left
open, per card, across visits and restarts. On a first visit only the first section is open.
Opening a section never scrolls the others away. Collapsing animates the height with the
emphasized easing, and the shape does not morph.

## 4. Controls (Material Components 1.14.0, the app's version)

| Control | Component and style |
|---|---|
| One of a few (Applies to, Position, Style, Icon size, Label size, Contrast, Mode, Default type, Rim strength, Clock position, 12/24 h, °C/°F, the 3-part pill) | `MaterialButtonToggleGroup` with `Widget.Material3Expressive.MaterialButtonToggleGroup` (2 dp spacing; `innerCornerSize` state list: 8 dp, 4 dp pressed, 50% checked); children `Widget.Material3Expressive.Button.TonalButton`, checkable, `singleSelection` + `selectionRequired`. The chosen one is filled (primary / on-primary) and fully round. No check icon, no outline. |
| On/off group (Status bar Elements, Dock Show/hide) | wrapping row of `Widget.Material3Expressive.Button.TonalButton` checkable buttons; shape `m3expressive_button_shape_state_list` (round when off, square 12 dp and filled when on). No check icon. |
| Single on/off | `MaterialSwitch` in a 56 dp row with a one-line summary |
| Picture choices (Presets, Clock face, Dock type, Cursor trail, Screen effect, Page transition) | the existing `PreviewTilesRow`, laid out as a wrapping grid (3–5 per row, nothing scrolls sideways); the chosen tile is filled (primary) with 22 dp corners, the others surface-container-high with 12 dp. No check overlay. Dock Type is one row of four. |
| Stepper (Columns, Rows, Apps in the dock) | the existing `StepperRow`; a limit disables its button and shows a one-line hint |
| Linked toggle | `Widget.Material3Expressive.Button.TonalButton`, checkable, icon `link` / `link_off`, centred, about half the row width |
| Sliders | the current M3 slider; value with its unit on the label line (20%, 24 dp, 1.0×, 3 dp) |
| Section card | `MaterialShapeDrawable` on surface-container-lowest, 20 dp; title row with an arrow |

`SegmentRow` is where every one-of row changes. `Widget.Termux.Button.Segment` and
`Widget.Termux.SegmentGroup` are also used by Settings pages (`SegmentedPillPreference`,
`TaiSegmentedTabs`); those stay untouched, and the editor gets its own styles.

Every arrow means something opens: › opens a card in the editor; ↗ opens a Settings page and
names it under the link ("Settings › Keyboard › Layouts"). The Colour ⌄ and More ⌄ rows that do
nothing today are gone.

## 5. Apps in the dock (new setting)

A per-dock limit on how many pinned apps the dock row shows. Apps past the limit go to the next
page of the apps dock, as filtered apps already do. The stepper's + is disabled at the most that
fit at the current icon size and style. The fit comes from the same test as today's
"N pinned apps fit up to M" hint (`IconSizeCap`), and the hint becomes "At most 7 fit at
size M." under the stepper when + is disabled. Changing the icon size re-clamps the limit.

## 6. Upgrade nudge (new)

Once, on the first open after upgrading from an earlier pre or a stable 1.x, for a user whose
glass values differ from the shipped ones. It uses the welcome/setup card style, shows after the
launcher is up, and never shows over a running terminal.

Text (the developer's, typo-fixed): "It is recommended that you reset the Glass options to
default to migrate safely to the new version. Please pick one from below." Four preset tiles
(Clear, Mist, Tint, Solid). Buttons **Decline · Accept**.

Accept resets **glass and colours only**: every look-grid cell on every element and page kind,
plus Mode, Contrast, Accent and Dynamic colour. It then applies the picked preset to
Customization. Layouts, wallpaper, icon packs, dock apps, terminal text and keyboard layouts are
kept. Decline never asks again. The same reset stays on Customization › ⋮ › Reset everything.

## 7. Words (changed labels)

| Today | New |
|---|---|
| Global; Theme, colours and glass; Theme | Customization |
| Look | Presets |
| Everywhere / All pages | Global (Applies to) |
| Where it applies / What it applies to | Applies to (one row) |
| All glass | Customization › Appearance |
| Frost | Blur |
| Tint + Colour (Primary ⌄) | Tint with swatches |
| Corners | Corner radius |
| Rim | Rim strength |
| Home layout / Terminal layout / Elements | Layout |
| Appearance (Dock section) | Appearance (the shared block) |
| Wallpaper colours / Match colours to wallpaper | Dynamic colour |
| Tool colours | TUI app themes |
| Sheets | Menus and dialogs |
| Indicators | Elements |
| Buttons (Dock) | Show/hide |
| Search field | Search bar |
| Text size | Font size |
| Split gap / Gap between panes | Inner gap |
| Margin (Terminal, Keyboard) | Outer margin |
| Type (Keyboard) | Default type |
| Key corners | Key corner radius |
| Background opacity | Keyboard opacity |
| Key opacity | Keys opacity |
| chain glyph / Move both opacities together | Linked (toggle button) |
| Wallpaper scroll | Wallpaper scrolling |
| Numerals | Numerals font |
| °F weather | °C / °F |
| Camera gap | Camera cutout ↗ |
| Essential notifications | Pinned notifications ↗ |
| Portrait chip | phone glyph in the header |
| ↻ | ⋮ › Reset … |
| ✓ on any chosen control | none: fill and shape carry the choice |

## 8. Bugs this removes

- Colour ⌄ and More ⌄ draw an expand arrow and do nothing (`EditorCardSheet.actionRow` draws
  `ic_editor_expand_more` for every `Kind.MENU` row; `SurfaceEditorController.onRowAction` has no
  case for `TINT_HUE`, and `SECTION_MORE` is a heading).
- Icon pack's tiles open below the visible sheet.
- The fifth Look tile (Custom) is clipped at 1080 px / 420 dpi.
- The Portrait chip looks like a button and does nothing.

## 9. Gating

Each wave gets an emulator pass at 1.3× font scale and at 360 × 640, then goes on pong before the
next wave (11-01 decision 9). Settings pages are not edited, except that the setup wizard's three
pane rows are reworded (§2 Terminal). Settings links only open existing pages.
