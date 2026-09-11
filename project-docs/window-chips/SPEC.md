# Window chips: the title gets the room

Agreed 2026-09-11 on a review page (`.lavish/window-chips.html`, not committed). Terse copy.

## Today

A chip's label is `[● ]?` agent dot + one process glyph + a space + ≤ 9 title characters (14 for a
named window), 10.5 sp in the terminal face, 20 dp tall, 3.5 dp side padding, 104 dp cap
(`TerminalWindowBar.tabText`, `createTab`). Busy replaces the glyph with `ProgressRingSpan`; bell,
done and failed marks take the same slot. Up to four of thirteen cells go to indicators. The phase 2
× (`ChipRevealPolicy`) is a 32 dp opaque pill laid over the selected chip's trailing end.

## Decisions

| # | Decision |
|---|---|
| C1 | **Glyph behind the title.** The chip outline wraps the text alone; the process glyph is drawn 16 dp, centred behind the title, clipped by the chip. Busy becomes the chip's own outline as ring. Marks and the agent dot become corner dots. |
| C2 | **× as a segment of the chip.** The selected chip grows 24 dp on its trailing side behind a hairline divider; the × shares the selected fill. Neighbours are pushed, never covered. |
| C3 | **Display chips get the app icon**, mapped from `WM_CLASS` to the drawer catalogue's desktop-file icon, drawn as a single-colour silhouette. No match → generic app glyph. |
| C4 | ~~Glyph strength 30 % of the text colour, +10 when selected~~ **Amended 2026-09-11 evening after the first pong look (glyph buried under the title):** glyph in the **place accent** at 15 %, 26 % when selected (halved again 2026-09-11 night at the user's request); **21 dp, filled**, anchored at the **leading edge** with the title nudged 5 dp; the title gets a **soft halo** in the chip fill so letters separate from the glyph. |

## Behaviour

- **Label** is the title only. Named window ≤ 14 code points; process labels keep `LABEL_MAX_CHARS`.
- **Glyph** = today's `processGlyph` map rendered from the Nerd Font face into a per-chip layered
  drawable under the text: 21 dp filled, place accent (primary when none) at 15 % (26 % selected),
  anchored 2 dp from the leading edge and clipped to the chip; the title starts 5 dp further in
  and carries a soft halo (1.5 dp shadow layer) in the chip fill colour. Constants, not settings.
- **Busy** = the chip's 1 dp outline: indeterminate travels a 270° arc around the rounded rect on the
  1280 ms turn, lazy mode keeps its 8 stops/160 ms; determinate fills clockwise from top-leading over
  a track at alpha 56. Colour `colorTertiary` as today. Text does not move.
- **Marks** = 5 dp dot on the top-trailing corner, 2 dp outside the outline, haloed 1 dp in the bar's
  ground: bell and failed in `colorError`, done in the busy colour; precedence bell > done/failed as
  today. **Agent dot** = same dot on the bottom-leading corner in the accent, breathing as today.
- **×** = measured child of `SelectionStrip`, width animated 0 → 24 dp over 180 ms with the bar's
  settle curve, inside the selected chip after a 1 dp divider in the selected stroke colour; the
  selection highlight follows the wider chip. Touch target 24 × 24 dp through a `TouchDelegate` on
  the status row. Reveal rules unchanged.
- **Display chips**: `WindowItem` carries an optional icon; a resolver matches `WM_CLASS` (class
  part, case-insensitive) against `LinuxAppCatalog` entries by desktop-file name, `StartupWMClass`
  or the `Exec` basename, loads through `LinuxAppIcons.load`, and converts to a silhouette
  (luminance → alpha, tinted like the glyph). Cached per class.
- **Column chips** (`StatusBarWindowColumn`, 26 dp) unchanged.

## Build plan

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 5 | `feat/chip-watermark` | Label = title; layered chip drawable (glyph, outline ring, corner dots, agent dot); × as an animated 24 dp segment with `TouchDelegate`; `WindowItem.icon` consumed when present; tests | dev ≥ d3154a8a (agent-status chips) | `TerminalWindowBarTest`, `ChipRevealPolicyTest`, new drawable tests; pong: busy shell, editor, agent pane, named window, × slide |
| 6 | `feat/display-chip-icons` | `WindowItem.icon` field + `WindowItem.withIcon`; `X11WindowIconResolver` (WM_CLASS → catalogue → silhouette), wired in `syncWindowBarItems` | — (touches only `WindowItem` in the bar) | unit: resolver matching + silhouette; pong: Firefox chip shows its icon |
| — | `fix/surface-editor-global-heading` (c2096c47) | already built; merges with these | — | compile |

## Risks

- Watermark legibility depends on the user's terminal face; strength is capped and the glyph is
  centred so the first letters stay clean.
- Some Nerd Font glyphs are dense at 16 dp; keep the current map, prefer outline variants where the
  face has both.
- The agent-status work merged 2026-09-11 15:46 also draws on chips; phase 5 must start from that
  dev head and keep its semantics.
