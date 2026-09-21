# Widget pages on demand

## Done
- Repository: trimEmptyPages() + fresh pages (addFreshPage/setPages/freshPages, persisted as
  "freshPages"); trimSparePages removed. Controller: trim on load/change/drag end, menuAddPage back,
  drag past the last page makes one. Frame: + action on the resting tab.
- Widget + wall suites green (273 tests).

## In progress
- Full module suite run, then commit.

## Next
- Nothing after the suite is green.

## Gotchas
- An empty *leading* page now goes too, so page indices renumber; currentPage is clamped after every
  trim. The + action is routed through WidgetPaneView (like the tick/cross), not through Host.
