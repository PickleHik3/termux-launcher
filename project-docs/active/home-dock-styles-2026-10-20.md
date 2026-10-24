# Home dock styles

> **2026-10-21:** changed by [`chrome-unification-2026-10-21.md`](chrome-unification-2026-10-21.md) (ADR 0009): the dock and its design are shared by both halves, Prompt line and Taskbar become the Search field and Drawer tile toggles, the run strip goes, and the designs are #1 Tray, #2 Beads, #3 Strip.

Home is a conventional launcher with no launcher status bar (ADR 0008), so its dock is where the
command palette, Home floats and Workspace terminals meet Home. This spec turns the dock into a
choice of five styles, picked in the Layouts menu. The developer chose them over four review rounds
on 2026-10-20 (mockup: `.lavish/home-docks/index.html` in the main checkout, gitignored). The
research behind the numbers is
[`../reference/launcher/home-dock-ux-research-2026-10-20.md`](../reference/launcher/home-dock-ux-research-2026-10-20.md)
(rules R1–R12 below are its "Rules for the mockups"). Rolling status and device checks:
[`home-dock-styles-ledger.md`](home-dock-styles-ledger.md).

## The choice

- One preference, **Dock style**, on Home's layout: `glass_tray` (default, and what every existing
  install migrates to), `lens_beads`, `taskbar`, `prompt_line`, `wall_strip`.
- Picked in the Layout editor while editing Home's layout, as a row of small previews, one per
  style. Settings pages are fragile (the settings look was broken repeatedly by agents): the picker
  is one new row in the existing Home layout surface, nothing restyled around it.
- The Workspace's dock is untouched. Every style keeps the existing glass knobs (Look → Base) and
  icon size, and every style uses the global glass, as widgets do.
- Switching style is live: no restart, the dock eases to its new shape.

## Shared rules for every style

- **Tiles** 58 dp (Icon size's default; R1), hit area at least 48 dp, gaps 10 dp. Five apps at
  ≥ 392 dp wide, four below (R3). The three most-used sit in the middle by the user's own order;
  the dock is never auto-sorted (R12).
- **Equal padding**: the space above the icons equals the space below them.
- **Page dashes** (the dock-page indicator, `PageTickStrip`) float just above the slab, never inside
  its top padding. They are also the visible cue that a swipe up from the dock's top edge opens the
  command palette with the keyboard (R4, R5).
- **Glass** (R10, R11):
  - The outline is rigid and crisp. Refraction lives in a soft rim band of about 24 dp that fades
    into the frosted face, never a hard-edged ring. No idle shimmer; nothing moves at rest.
  - **Lower shelf**: the slab reads as a thick piece of glass, not a hard cut. A brighter lip where
    light leaves the lower edge, a slightly thicker lower edge, a soft contact shadow and a faint
    light glow on the wallpaper beneath.
  - **Icon reflections**: the icons are reflected faintly in the face (about 4–5 %), more clearly
    in the lower bevel (about 25–40 %, less on low slabs so they never smear), and squeezed in the
    top bevel. Reflections follow the live tile order and positions and shift by twice the slab's
    tilt under living glass. They must stay subtle; the developer flagged them on the Taskbar.
  - Text sits on a darker well where needed: 4.5:1 for text, 3:1 for the slab's edge (R9).
- **No clock or readings in the dock** except the Wall strip's clock. **There is no "Ask"**: never
  show an Ask row, button or glyph.

## The styles

### Glass tray (default)

Today's plank refined to the shared rules: one slab, five tiles, equal padding, page dashes above
the slab. A minimised Home float shows as a chip centred just above the tray.

### Lens beads

No plate. Each app sits on its own small round glass lens (bead) slightly larger than the icon, with
a tight, stronger refraction band and its own reflection. The beads keep the 10 dp rhythm. A
minimised float is a sixth bead above the row's end with a badge. Long-press on a bead keeps the
existing app menu.

### Taskbar

- The dock holds only tiles: a **Home tile** (opens the drawer, same as today's Home/drawer entry)
  and the apps, all at full tile size. No clock, no readings.
- The Home tile is movable: long-press and drag to the start, the middle or the end (persisted). Its
  reflection moves with it.
- A thin glass **run strip** floats above the dock (a Waybar workspace list): one numbered entry per
  running window (Workspace windows, Home floats, display windows), the current one lit, a
  minimised float marked, and `+` for a new window. It uses the existing window chip model and
  attention signal, appears only while something runs and hides when nothing does. Tap an entry to
  go to it.

### Prompt line

- Below the apps row, a permanent **command palette search field** styled as a shell prompt
  (`~ ❯ Search apps, commands, settings`, segments in the terminal theme).
- Tapping it opens the command palette and keyboard with the cursor already in the field; it is the
  palette's own search, and Enter opens the top result. It does not run shell commands itself.
- The field is a darker well inside the slab.

### Wall strip (on the expanded dock)

The dock gains a first row for the Workspace above the apps row. Both rows share the apps row's
14 dp side inset, so the clock's left edge lines up with the first icon and the last tile ends where
the last icon ends.

| Terminals open | First row |
|---|---|
| 0 | A large clock with the date (about 56 % of the row) and a rounded `+` tile filling the rest; tapping `+` opens a new terminal. |
| 1 | The clock, then the terminal tile and the `+` tile as two equal tiles. |
| 2 | Clock and terminal area 50/50; two terminal tiles and `+`, all equal. |
| 3 | The clock slides out to the left; the three terminals and `+` split the row equally. |
| 4 | The same, narrower (five equal tiles). |
| 5+ | Tiles keep the four-terminal width; the row scrolls, `+` at the end. |

- No gap between the clock and the terminal area. The `+` tile is always the size of a terminal
  tile.
- Tiles nest in the slab: corner radius is the dock's corner radius minus its padding. Each tile
  shows its window's split shape and title, the current one with an accent ring.
- A window that needs attention (the same signal the window chips use) gets an amber ring and dot.
- Tap a tile to open that window; long-press keeps the window's existing menu.
- Home's own clock widget is the user's business; this style does not add or remove widgets.

### Peek glass (2026-10-25; the default of a new install)

A glass card rises from the screen's bottom edge, inset about 3% of the width from each side, its
top corners a generous 28 dp, its lower corners past the screen's edge so they never show. The
icons' centres stand on the card's top edge (round icons half above it), no labels, and blurred
mirror reflections of the icons fade down the card. The gesture pill draws over the card; the
strip under the pill is the card's foot, inset as the card is.

- **Picker and default.** The Dock card's Design chips offer it as "Peek" (working name;
  `DockStyle.PEEK_GLASS`, stored `peek_glass`). A new install starts on it: the layout store's
  first run writes `peek_glass` into every layout's `dock_design` only when the store was never
  migrated and the package's first install time equals its last update time. The absent key
  still reads as the Glass tray, so an existing install that never picked keeps the tray; an
  unknown or unbuilt id still falls back to the tray.
- **Glass.** The global Look (Base), as every dock: the host's frost, tint and glass program,
  cut to the card (the host's outline; the program's rect is the card's, its bottom a seam). Below
  Android 13 the rim under the glass follows the card's top and sides.
- **Geometry** (`dock/PeekGlassGeometry`, unit-tested): side inset `round(0.03 × width)`; the apps
  row's side inset under Peek glass Docked is that plus 4 dp, so the end icons stand on the card;
  the card's top is the icons' centre line; the reflection hangs from the icons' lower edges over
  0.6 of an icon's height, never past the screen's edge.
- **Reflection.** Live while Fancier glass is active (Android 13, managed wallpaper, the switch):
  the row recorded flipped into a RenderNode whose icons are references to their own nodes, so a
  press, a dot or a new icon shows with nothing recorded again; a RuntimeShader effect fades it
  as (1 − t)² and blurs it up to 6 dp. Recorded again on layout, on an icon's invalidation (once
  a frame at most) and on each frame of a page swipe; nothing at rest. Otherwise one flipped
  quarter-size bitmap per icon with the fade drawn in (a light RenderEffect blur from Android 12),
  rebuilt 160 ms after the row last changed and put away while a swipe carries the icons.
- **Reflection knob.** One Reflection slider on the Dock card, shown only under Peek glass:
  0 (off) to 100, default 50 (`dock_peek_reflection`).
- **Where it is the tray.** Floating, the keyboard up, the system keyboard lifting the dock, the
  Search field under the row, another band on the dock's glass (A–Z row, extra keys), a joined
  Docked frame standing in for the dock's sheet: the dock draws the Glass tray's slab, shelf and
  shadow included, and no reflection.
- **Not reflected.** The long-press app menu: a window of its own, which only a capture of that
  window on every frame could reach.

## Order of work

1. The Dock style preference, its migration and the Layout editor picker.
2. Glass tray with the shared glass work (equal padding, dashes above, shelf, soft rim band, icon
   reflections). Every later style builds on it.
3. Lens beads. Then an emulator pass (device-checker) on 1–3 and fixes.
4. Taskbar with the run strip, Prompt line, Wall strip. Then a second emulator pass and fixes.
5. A release build on pong.

Each step is checked on the emulator at the end of its phase; pong gets one install when all five
are done.
