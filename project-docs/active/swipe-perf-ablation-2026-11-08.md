# Swipe performance: what the frame time is made of (2026-11-08)

One session on pong (90 Hz, 11.1 ms per frame), one build, six settings flipped at run time. Plan:
[swipe-model-2026-11-08.md](swipe-model-2026-11-08.md). Decision record: [ADR 0023](../../docs/adr/0023-the-chrome-stays-put-pages-carry-their-own.md).

## Method

- Build: `diag/swipe-ablation` (8fcc3b884) = pushed `v2-redesign` aace02518 + `fix/keyboard-under-dock`
  + `fix/rising-keyboard-inks` (logging reverted) + run-time switches read from `~/.tl-swipe-diag`
  at each drag start, with per-frame counters. Never merged.
- Six settings, four sets each, order A B C F L S S L F C B A played twice, so drift cancels.
  A set is the traced Home2 ↔ Workspace pair (10 swipes) and Workspace ↔ AI pair (11 swipes),
  driven by `input swipe` (250 ms, fixed path) from Home1. Two sets disturbed by a pocket touch
  were rerun.
- Metrics, inside each swipe window (`Wall.beginDrag` → `Wall.pageSettled`):
  - **skipped**: share of present-to-present gaps over 1.5 periods (16.7 ms), i.e. a refresh
    actually skipped. The old "> 13 ms" count also counted on-time jitter and roughly doubled
    the figure.
  - **late**: FrameTimeline frames marked App Deadline Missed.
  - Average UI-thread `Choreographer#doFrame` and RenderThread `DrawFrame` per frame.
- Run-to-run spread within the session: ±0.5–2 points. Results from different sessions drift by
  up to 10 points and must not be compared.

## Results

| Setting | H2↔WS skipped | WS↔AI skipped | H2↔WS late | WS↔AI late | UI / RT ms (H2↔WS) |
|---|---|---|---|---|---|
| A: baseline (Glass look, Live blur on) | 9.5% | 11.3% | 13.4% | 20.9% | 9.6 / 8.0 |
| B: no per-frame glass or frost re-aim | 8.6% | 9.3% | 12.2% | 17.6% | 8.9 / 7.9 |
| C: no hardware layers on moving pages | 9.2% | 12.6% | 10.0% | 22.3% | 7.8 / 6.2 |
| L: Live blur off | 9.5% | 5.4% | 11.9% | 12.8% | 9.6 / 8.0 |
| F: B + C + live pictures frozen while moving | 7.6% | 2.7% | 8.1% | 10.4% | 6.5 / 6.0 |
| S: no glass (Solid, Fancier glass off, Live blur off) | 4.5% | 2.2% | 4.2% | 0.9% | 6.1 / 4.0 |

UI-thread time per run (10 swipes) at baseline: `LiveBackdrop.onPreDraw` ~0.8 s,
`PaneGlass.onDraw` ~0.3 s. The worst pause (~50 ms) and the count of pauses over 27.8 ms barely
move in any setting, including with no glass.

## What it means

- Glass is about half of every swipe frame: with it off the UI thread drops 3.5 ms and the
  render thread halves, and steady frames fit the budget.
- The cost is spread, not one culprit: live-blur planning per frame (~1.5 ms UI), the per-frame
  pane glass and frost re-aim, and page layers re-rendered because their contents are dirtied
  every frame. Together (F) they take Workspace ↔ AI down to the no-glass floor.
- Two stalls per swipe do not depend on glass: the drag-start frame and the landing frame. They
  are what remains on Home2 ↔ Workspace.

## Next, by payoff

1. Glass that only translates while a page moves: aim pane glass and frost in screen space so a
   move does not re-record them, and stop re-planning live pictures each frame. Ceiling, from F:
   WS↔AI 11% → ~3%, H2↔WS 9.5% → ~7.5%. The switches as built look wrong; this is a real change
   to glass anchoring, not a flag.
2. Remove the drag-start and landing stalls (ADR 0023 §4: chrome prepared at finger-down, nothing
   rebuilt at landing).
3. Parked: wave 2b-1 (hosts installed once), wave 4 (lighter glass while moving, dropped), the
   kept-glass budget. They target costs this test shows are small.

## Fixes checked in the same session (round 25)

- Keyboard number row under the nav pill after Workspace → Home2: gone (`fix/keyboard-under-dock`).
- Toolbar icons dim while the keyboard rises on Home2 → Workspace: bright from the first rising
  frame (`fix/rising-keyboard-inks`).
- Special keys darkening at landing: open; one `RISEINK-DBG` capture taken.
