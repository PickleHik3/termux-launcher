# Kitty text sizing (OSC 66) — "K2" (2026-09-21)

Decided on the review page `.lavish/kitty-text-sizing.html` (Lavish session 1c0f21c00d304030).
Deferred from the kitty features round (`project-docs/kitty-features/SPEC.md`). Trigger: `dawn`
draws its headings with `OSC 66 ; s=2 ; text ST`; the emulator had no OSC 66 (its "66" mentions
are DEC mode 66). Programs detect support by cursor position after a `w=2` write, so nothing is
advertised.

## Protocol summary
`ESC ] 66 ; key=val:key=val ; text ST`. Keys: `s` 1–7 scale (block is s rows × s·w columns),
`w` 0–7 forced width in cells before scaling (0 = measured), `n`/`d` fraction n/d of the block
height (0 = whole), `v` 0–2 vertical align (top/bottom/centre), `h` 0–2 horizontal
(left/right/centre). Cursor moves s·w cells right on the same row; autowrap applies between
blocks; a block larger than the screen is discarded; writing over the top-left cell erases the
whole block, over another cell blanks that cell; ICH/DCH/ECH/EL/ED/IL/DL and scroll drop every
block they intersect; combining marks attach to the block; payload ≤ 4096 bytes UTF-8.

## Decisions
- D1 A — the whole protocol: s, w, n/d, v/h; s capped at 7.
- D2 A — the cursor grows to cover the block (block cursor fills it, bar/underline span its width).
- D3 — B — shrink and grow back. When the column count changes, each anchor is replayed with its stored record: if the full block fits it is written as a block again; if not, the anchor text is written as normal-size cells on the anchor row, still carrying the record with a demoted flag, and the rows the block used stay blank beneath. On a later resize where it fits, a demoted anchor is promoted back to a full block, but only into blank cells. Text after the block on the same line rewraps as any line does. Row-only resizes leave records untouched. Confirmed by the user 2026-09-21 ("B confirmed") after the assessment on the page.
- D4 B — the row cache learns tall nodes: an anchor row and its s−1 rows form one cached layer,
  drawn, dirtied and evicted together; rows without blocks keep one-row layers.
- D5 A — a block is one selection unit: touching any part selects all of it; copying yields
  its text once (continuation cells contribute nothing).
- D6 A — two phases, model then renderer, with a ≤40-line contract between them.

## Design
- One lazily allocated `int[]` side table per `TerminalRow`, one entry per column, zero for a
  normal cell. Packed: scale (3 bits), width (3), x offset (6), y offset (3), n (4), d (4),
  v (2), h (2), demoted flag (1). The anchor is the cell with x=0,y=0 and holds the text as one
  cluster; continuation cells hold only the offsets and are resolved as (row − y, col − x).
- `KittyTextSizing` parses metadata and text (pattern: `KittyNotifications`), dispatched from
  `doOscSetTextParameters` case 66. With `w=0` each character is its own block; with `w>0` the
  whole text is one block of s × s·w cells.
- Erase rules live in `TerminalBuffer`: one helper drops every block intersecting a region,
  called from every erase/insert/delete/scroll path. REP repeats with the same size record.
- Renderer: continuation cells draw nothing and break the run; a block pass draws each visible
  anchor at text size × s (× n/d), clipped to the block, aligned by v/h, background from the
  anchor's style. Blocks whose anchor row has left scrollback draw blank.
- Tapping and mouse reporting map to the real cell (protocol: cursor movement is unaffected).

## Build plan
| Phase | Branch | Worktree | Deliverable | Depends on | Model |
|---|---|---|---|---|---|
| P1 model | feat/text-sizing-model | app/tl-wt-text-sizing-model | size record on TerminalRow; OSC 66 parser; emit, wrap, discard; erase rules; REP; reflow per D3; copy-text per D5; unit tests for every protocol rule; contract for P2 | — | opus |
| P2 renderer | feat/text-sizing-draw | app/tl-wt-text-sizing-draw | run breaking, block pass, multi-row cache nodes (D4 B), cursor over block (D2), selection growth (D5); renderer tests | P1 contract | opus |

## Gates
- terminal-emulator and terminal-view unit suites green on the merged state (437 and 138
  today, plus the new tests).
- Waydroid: a script prints s=2, s=3, w=2, a fraction, then overwrites, erases, inserts a line
  and resizes; screenshots checked by pixel rows. Dawn's own probe must answer "supported".
- pong: only after the user's explicit yes to install; no dev merge until the user's cue.
