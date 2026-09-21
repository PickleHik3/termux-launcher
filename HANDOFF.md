# Widget pages on demand — done

## Done
- 8155771a: trimEmptyPages() + hand-added ("fresh") pages in the repository, trim on load / every
  repository change / drag end, drag past the last page makes a page, + on the corner tab, tests.
- Follow-up commit: discard also trims, so a binned widget's page does not come back.

## In progress
- Nothing.

## Next
- Waydroid gate: drag past the last page, the +, and emptying a page.

## Gotchas
- An empty *leading* page goes too, so pages renumber; currentPage is clamped after every trim.
- The + is routed through WidgetPaneView (like the tick/cross), not through WidgetPaneFrame.Host.
