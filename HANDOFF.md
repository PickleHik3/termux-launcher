# P2 text sizing renderer (feat/text-sizing-draw)

## Done
- `TextBlockGeometry`: pure block rect / size scale / alignment / cursor rect maths.
- `TerminalRenderer`: block cells break the run; `drawRowTextBlocks` + `drawTextBlock` draw each
  anchor at scale (x n/d), clipped and aligned; cursor span covers a block (D2) on both paths and
  in `drawExtraCursors`; the record loop is split into compare-then-record for D4.
- `RowRenderCache`: `captureTextSizes` + `spreadTextBlockGroups` (D4 B group dirtying).
- `TextBlockSelection` + controller wiring (D5).

## Next
- Tests: RowRenderCacheTest (group dirtying, record change), TextSizingRenderTest (geometry),
  TextBlockSelectionTest.

## Gotchas
- `TerminalRow.getTextSizeRecord` is package-private, so the cache rebuilds a comparable key from
  the typed accessors (`textSizeKey`).
- `getWordAtLocation` is an emulator fix, not this phase's; described in the report.
