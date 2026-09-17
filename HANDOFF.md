# fix/place-marks — status bar place marks take the key row's colours

## Done
- PlaceSwitchGlyph: UNFOCUSED_ALPHA moved here (single source); GLOW_RADIUS_DP now 0 (no halo).
- ExtraKeysView: publishes each place switch's glyph colour by key value (listener + getter),
  fired from restateEveryKey and reload; focused key no longer sets a shadow layer.
- TermuxActivity: maps key value -> PaneWallPage (ExtraKeyEligibility) and feeds the lens.
- StatusBarLensMetrics: Mark.drain/NEIGHBOUR_DRAIN replaced by Mark.glyphInk + glyphInkFor.
- StatusBarLensView: setPlaceAccents(); marks painted in the row's colour, neighbours faded.
- Tests updated (lens policy, ink sweep, key style) + new StatusBarLensAccentTest.

## Next
- Nothing outstanding; full suite run before the final commit.

## Gotchas
- StatusBarInk.java belongs to wt-small-0917 this round: its drain() is now unused by the lens
  and its javadoc still says the lens uses it — left for the orchestrator to fold in.
