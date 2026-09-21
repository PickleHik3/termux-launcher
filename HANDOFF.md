# P2 text sizing renderer (feat/text-sizing-draw) - done

## Done
- `TextBlockGeometry`: block rect, drawn size (s x n/d, 1 when demoted), v/h alignment, baseline,
  per-row cursor rect, and `selectionCovers` / `cellSelected` - a block's cells take their fill
  from the anchor cell, which is what highlights the rows under a tall block.
- `TerminalRenderer`: block cells break the run; `drawRowTextBlocks` / `drawTextBlock` draw each
  anchor clipped and aligned; cursor covers a block (D2) on both paths and for extra cursors;
  record loop is compare-then-record for D4; a `Selection` holder replaces the per-row selx pair
  in the background pass.
- `RowRenderCache`: `captureTextSizes` (compares the public packed record) and
  `spreadTextBlockGroups` (D4 B); rows carrying blocks also re-record whenever the selection moves.
- `TextBlockSelection`: D5, one-row rule - both ends snap to their block's run on its anchor row,
  put back in stream order over both blocks' corners. A block rectangle cannot be said in a
  stream selection, so the rows underneath are the renderer's job, not the selection's.

## Next
- Nothing here. Waydroid re-check of the long-press highlight and of copy is the round's gate.

## Gotchas
- Snapping an end down to a block's bottom right is what painted a band across the whole row and
  copied every block on it: a two-row selection is a stream, not a rectangle.
- Handles stay on the anchor row's cell edges; they are not moved to the block's bottom.
