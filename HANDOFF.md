# feat/help-overview — the curated overview in front of the guide

## Done
- `HelpLeaderRouter`: `arrange`/shelves/lanes/`connect` restored from 76efa730, `route`/`paths`/
  `valid`/`Placement.lane` pruned; `HelpLeaderRouterTest` back to 20 tests.
- `HelpOverlayView`: OVERVIEW mode (`overview(place)`) — curated cards, key labels, ×/Guide buttons
  in a wall corner, no markers, empty taps ignored, Back not consumed. EXPLORE unchanged.
- `HelpPresentationModel.OVERVIEW_TARGET_IDS` (dock, status, prefix, settings) + `overview()`.
- Key labels filtered to launcher keys (`HelpCopy.isLauncherKey`, used in `HelpTargets.extraKeys`).
- `HelpController.show` opens the overview; Guide → sheet home, card → topic, Back → close.
- Strings appended: help_dock_rail_action, help_overview_guide(_action), help_key_paste.

## In progress
Nothing.

## Next
Device check on pong: the overview on all three places, keyboard up/down, dock as a rail.

## Gotchas
- The 400x800 Robolectric harness cannot hold seven wordy key labels; the view-level key tests use
  a three-key launcher row and the shipped seven are checked in the pure filter test.
- The buttons take the least-covered wall corner (no ? anchor is passed to the overlay any more).
