# HANDOFF — feat/widget-cross-page-drag

## Done
- Main code for all three changes compiles (WIP commit below).

## In progress
- Tests: WidgetPanePagingTest/WidgetPaneMenuPolicyTest need the ADD_PAGE removal folded in;
  new tests for the trim, the cross-page drop and the ✓/✕ tab not written yet.

## Next
- Run `:app:testDebugUnitTest --tests 'com.termux.app.launcher.widget.*'`, then the full suite.

## Gotchas
- The dragged cell stays attached (WidgetGridView.setDragPinned) or the touch stream dies.
- HELP must stay the trailing tab button: WidgetPaneFrameTapTest measures it from the tab's edge.
