# Terl decisions, 2026-11-28

The developer's answers to the open questions from the Terl build (Lavish decision page, 2026-11-28), after
using Terl on pong. They override the spec (`tiling-desktop-2026-11-27.md`) and earlier orchestrator rulings
where they differ.

## Glass
- **Terl glass joins the glass service** (ADR 0021, 0029): the control bar is the dock's Live entry, Terl's
  sheets register as panels, the sheets' opacity floor is ADR 0031's (not opaque). Not a new decision: the
  ADRs already said so; the build had bypassed them.
- **Every window's glass is Live**, with a residency protocol that loads and discards Live pictures ahead of
  need, as a game streams its world (design to follow, numbers from the repo's measurements or fresh ones on
  pong). Replaces "tiles on the stored frame".
- **Blur behind see-through Linux windows**: built (Windows Blur applies behind them, not only Opacity).
- **The wall's dim under Terl** is the page's Wallpaper dim (ADR 0031 §4). v1's Docked frame glass is not
  drawn under Terl; Terl has no Docked/Floating.

## Looks and edit mode
- **Black theme** added (true-black surfaces); the accent comes only from the wallpaper (no accent picker).
- **All glass card** as the prototype on ADR 0033's presets: Clear, Frosted, Tinted, Solid, Liquid, plus
  Custom.
- **Look filmstrip**: only the shipped presets and one Custom, each drawn as that glass (not bare squares),
  all in one row without horizontal scroll. **The three Looks made from the wallpaper are removed** (reverses
  #447's wallpaper Looks; their names truncated and they crowded the row).
- **Home card** as the prototype: Grid columns 3 / 4 / 5 chips and a Labels toggle.
- **Edit mode shrinks the windows** above the lifted bar and strip (reverses the 2026-11-28 ruling to clip
  them behind the bar).
- Default look: Clear; Custom starts from Clear; corners 26 dp (max 48). Confirmed.

## Home
- **A long press on Home opens a small menu**, not edit mode directly: Edit Home / looks, Settings, New
  terminal, and only other items of the highest value. Edit mode is one entry of it.
- The card edit mode raises from the bottom must not cover the bar or the A–Z bar.
- **The A–Z bar**: at the bottom it joins the control bar's glass instead of standing as a separate pane; on
  the left or right its letters are grouped toward the bottom of the screen, not spread over its full height.

## Search
- **The bar's Search opens v2's command palette**, polished, instead of the new `barsearch` sheet. The palette
  follows the Global look (add that if it does not). The Assistant entry stays; section headers uppercase as
  the prototype.

## Kept as built (confirmed)
- The bar's Terminal on Home goes to the open terminal; the + tool opens a new one.
- Overview's Home row keeps its Page cards.
- New windows in portrait open half width beside the others; landscape a third.
- Navigation mode: windows dimmed with a rim, no zoom-out.
- Upgrade from v1 (#459) fates as built; Copy as config by hold only; v1's cutout "off" reads as Automatic;
  leader+F full screen, leader+V float; the stack model decisions of #415 and #421.

## Changed
- **Reply from a held notification replies in place** (#458), not via the notification stack.

## Round 2 (after the per-surface controls review and the live-window design)
Review: v2's terminal reads `~/.config/kitty/kitty.conf` only for fonts and cursor-trail lines (never writes it);
the colour scheme is "Wallpaper colours" or `~/.termux/colors.properties`; cursor style and blink are
`termux.properties` keys v2 never exposed.
- **Colour scheme**: Wallpaper colours, or a built-in scheme picker on the Windows card's Text tab; the app writes
  `colors.properties` for a picked scheme.
- **Cursor**: Cursor (Block / Bar / Underline) and Blink rows on the Text tab (written to termux.properties).
- **Upgrade** carries "Wallpaper colours" (and the keyboard theme, per-key colours and typeface, below).
- **Font size**: a Font size row on the Text tab (every terminal) plus pinch zoom per window.
- **Keyboard card**: Extra-keys row on/off plus an "Edit keys" link to the editor; Height and Key spacing; key
  corners follow Global Corners; a "Keyboard style" link to v2's keyboard page (theme, per-key colours, typeface,
  all carried on upgrade).
- **Windows card**: Rim on/off added; the 6 rim colour presets stay.
- **Home card**: Icon size and Monochrome icons; the other Home and icon settings in Settings › Home.
- **All glass card**: Depth and Shine (ADR 0033's app-wide controls).
- **CRT extras and Adaptive legibility** (screen events, composite signal, idle animations, glitch rate) under the
  Text tab's Effects, collapsed.
- **Live windows**: every window Live, memory by residency rings (hot on screen holds GPU layers; warm one gesture
  away holds views, no pixels; cold holds nothing; promotion ahead of a gesture, hysteresis on eviction). A tile
  with only wallpaper under it draws the stored frame at the same strength and counts as Live (no layer of its
  own); Floats, a carried window, sheets, the bar and tiles over a live scene hold real blur layers. Design:
  `live-window-residency-2026-11-28.md` (this folder).
- **Live blur** on by default under Terl above 6 GB of RAM, off below.
- **Tiles over a live wallpaper scene** follow the scene, on-screen windows only.
- **Jumping across several Workspaces**: a short fade to the destination.
- **Measuring on pong**: when the build is ready, ask; the developer says when they are off the phone.

## Round 3 (Settings, the AI page, first setup)
- **Settings root**: Home, Workspace, AI, Keyboard & input (every keyboard setting and the keyboard's behaviour
  over the display), plus the others (System: backup, permissions, about, updates). Each group holds everything
  about its place.
- **The AI page**: a Home setting adds the Assistant as a page to the left of Home 1 (as other launchers' feed).
  The Assistant is no longer a window kind in Workspaces: Workspaces hold terminals and Linux apps only. Search's
  Assistant row goes to the AI page (offering to turn it on when off). Reverses part of ADR 0025 and #419; an ADR
  records it.
- **AI is a core part of the launcher**: the welcome pages (to be designed) detect the phone's hardware and offer an
  offline model or a free cloud API key; until then the app nudges users to turn the AI page on. Free key research:
  `docs/research/free-ai-api-keys-2026-11-28.md`.
- **Mesa on first setup**: right after the bootstrap, the app installs Termux's `mesa` package and tlstore's
  `mesa-wayland-egl` at matching versions, in the background with a progress notice, retrying quietly when
  offline; the welcome pages take this step over when they exist.
- **The control bar in the app drawer**: off by default; "Show the control bar in the drawer" in Settings › Home ›
  App drawer.

## Round 3, windows (adding, splitting, resizing and moving windows)
Override "New windows in portrait open half width beside the others; landscape a third" under Kept as built.
- **Words**: a **window** is one terminal or one Linux app; a **Workspace** is the screen holding them (stacked
  under Home).
- **A new window** (the bar's + and leader+Enter) is a full-width column to the RIGHT of the focused window; the
  view scrolls to it. (Was half width beside.)
- **Split** is a separate action (the bar's split tool; a chord): divides the focused window along its longer side,
  top/bottom in portrait and side by side in landscape. In Scrolling a top/bottom split is a stack in the column;
  side by side is two columns at half width.
- **To the left**: leader+Shift+Enter opens the new window to the left of the focused one.
- **Resizing by drag**: any gap, side by side or top/bottom, takes a 24 dp touch strip with a handle shown while
  dragging; frames follow the finger; terminal grids and Linux apps resize once on release (ADR 0019); a double tap
  snaps to the nearest preset. Free widths and heights between sensible minimums.
- **Outer edges** keep the border hold (stepping); only gaps between windows resize.
- **Moving**: leader+Shift+←/→ swaps the focused column with its neighbour; on a window stacked in a column it
  leaves its column (becomes its own column) or, at the column's edge, joins the neighbour's column (niri
  consume/expel).
- The full chord table follows in a later round; the bindings stay in `TerlDefaultBindings` (one key owner).
