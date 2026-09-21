# P2 text sizing renderer (feat/text-sizing-draw) - done

## Done
- `TextBlockGeometry` (new): block rect, drawn size scale (s x n/d, 1 when demoted), v/h
  alignment, baseline, and the cursor rect per row of a block. Pure maths, JVM-testable.
- `TerminalRenderer`: block cells break the run and draw nothing; `drawRowTextBlocks` /
  `drawTextBlock` draw each visible anchor once, clipped and aligned, with the block's own
  decorations; cursor spans the block on both paths and in `drawExtraCursors` (D2); the node
  record loop is now compare-all-then-record, for D4.
- `RowRenderCache`: `captureTextSizes` (records + group reach) and `spreadTextBlockGroups`
  (D4 B: the rows a block spans dirty and record together). `rowChanged` gained
  `cursorColumns` / `cursorLastRow`.
- `TextBlockSelection` + controller wiring: D5 snapping of both selection ends.
- Tests: RowRenderCacheTest +8, TextSizingRenderTest (14), TextBlockSelectionTest (6).
  View 138 -> 167, emulator 474, all green.

## Next
- Nothing here. Device check of a real `OSC 66 ; s=2` line is the round's gate.

## Gotchas
- `TerminalRow.getTextSizeRecord` is package-private, so the cache rebuilds a comparable key from
  the typed accessors (`RowRenderCache.textSizeKey`).
- `terminal-view/build.gradle` gained `unitTests.returnDefaultValues` (as terminal-emulator has),
  so a test can build a `TerminalBuffer` without SystemClock.
- `getWordAtLocation` is an emulator fix; described in the report, not made here.
