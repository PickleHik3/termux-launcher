# Phase C — Explore this screen (branch feat/help-explore)

## Done
- `HelpExplorePlacement` (pure, new) + test: one card's seat, hard rules, explicit NONE.
- `HelpOverlayView` rewritten to exploration only: markers, one card, toolbar, per-topic gesture.
- `HelpPresentationModel` slimmed to place/measured/selection/colour; tests rewritten.
- `HelpLeaderRouter` pruned to Box/Segment/Side (the overview's router went with the overview).
- `HelpTargets.Target.copy` retired; 35 dead help strings removed, 3 explore strings added.

## In progress
- Running `:app:testDebugUnitTest --tests 'com.termux.app.help.*'` and fixing what it says.

## Next
- Report: judgement calls (deprecated shims for TermuxActivity, router pruning, no marker grouping).

## Gotchas
- TermuxActivity still calls the old 3-arg constructor/show(); deprecated shims keep dev compiling
  until phase B rewires it. Do not delete them on this branch.
