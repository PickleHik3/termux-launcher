# feat/widget-drag-to-add — handoff

Done: hold a picker card, carry the widget out of the sheet, drop it on a cell.
- WidgetPickerAdapter.CardHold watches the press without taking it; the tap path is untouched.
- WidgetPaneView.beginCarry cancels the child gesture and owns the stream in dispatchTouchEvent.
- WidgetDragLayerView draws the drop ghost (same paints as the edit chrome's).
- WidgetPaneController: CarryState, snapMove under a sentinel id, displacement preview hoisted
  off EditState, beginAddAt() shared by tap and carry.

In progress: tests.
Next: WidgetPickerDragToAddTest + full suite.
Gotchas: displaced neighbours must be committed BEFORE beginAdd (canReserve needs the cell free);
they are rolled back if the add is refused. No page flipping during a carry — out of scope.
