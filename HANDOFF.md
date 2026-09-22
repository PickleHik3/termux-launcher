# feat/widget-drag-to-add — handoff

Done (all of spec item 9 / D3=B), suite green at 5683 tests:
- WidgetPickerAdapter.CardHold watches a card press without consuming it; at the long-press
  timeout it reports onProviderHeld. Movement past slop drops it, so scrolling is unchanged and a
  tap is still exactly the tap it was.
- WidgetPaneView.beginCarry cancels the child gesture (synthetic ACTION_CANCEL through super) and
  owns the rest of the stream in dispatchTouchEvent; that is how one finger survives the sheet.
- WidgetDragLayerView carries the card's slot picture at grid-cell size and draws the drop ghost,
  in the edit chrome's own two colours.
- WidgetPaneController: CarryState + WidgetEditPolicy.snapMove under a sentinel id, so the carried
  widget gets the same snap and the same neighbour displacement a move does; beginAddAt() is the
  one bind path a tap and a carry share.

In progress: nothing.
Next: device gate (main session) — the handoff is the part no unit test can prove.
Gotchas: displaced neighbours must be committed BEFORE beginAdd (canReserve needs the cell free);
they are rolled back if the add is refused. Off-grid drop cancels, it does not reopen the sheet.
No page flipping during a carry — out of scope, the tap path also only ever adds to this page.
