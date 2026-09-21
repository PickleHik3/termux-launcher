# HANDOFF — feat/widget-cross-page-drag

## Done
- Cross-page drag, spare-page rule, ✓/✕ on the editing tab — all three, with tests (5480 green).

## In progress
- Nothing.

## Next
- Device/Waydroid gate: three pages, drag page 3 → page 1, page 3 goes and page 2 stays;
  ✓ and ✕ on the border tab; the red ghost and the "No room on this page." notice.

## Gotchas
- The dragged cell stays attached (WidgetGridView.setDragPinned) or the touch stream dies.
- A render inside a drag resets the overlay's touch mode: WidgetEditOverlayView.resumeMoveDrag.
- HELP must stay the trailing tab button: WidgetPaneFrameTapTest measures it from the tab's edge.
