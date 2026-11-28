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

## Open
- The Windows card's terminal colour scheme and cursor: the developer asked for a full review of the controls
  v2-redesign offers per surface (status bar, dock, terminal panes, extra keys, keyboard) before deciding.
