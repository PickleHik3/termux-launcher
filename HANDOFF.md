# Phase C — Explore this screen (branch feat/help-explore)

## Done
- `HelpExplorePlacement` (pure, new) + test: one card's seat, hard rules, explicit NONE.
- `HelpOverlayView` is exploration only: numbered named markers, one card, toolbar at the far edge,
  per-topic gesture (a rail swipes inward), remeasure keeps or reports the selection.
- `HelpPresentationModel` slimmed to place/measured/selection/colour; its test rewritten.
- `HelpLeaderRouter` pruned to Box/Segment/Side; `HelpTargets.Target.copy` retired; `HelpCopy`
  is now just the extra-key label. 35 dead strings out, 3 explore strings in.
- Acceptance test green: 103 help tests, 0 failures.

## In progress
- Nothing.

## Next
- Phase B rewires TermuxActivity to the seam; then the deprecated shims below can go.

## Gotchas
- TermuxActivity on dev still calls the old 3-arg constructor / show() / setPractice*; deprecated
  shims keep it compiling. Do not delete them on this branch.
- The Robolectric harness needs `qualifiers = "w400dp-h800dp"`: without it the window clips
  anything below ~470px and controls simply do not measure.
