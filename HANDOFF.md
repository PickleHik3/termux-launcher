# Phase A — the help model (branch feat/help-model) — done

## Done
- `HelpTopics`: one global topic record (group/kind/summary/action/steps/wayBack/reveal/
  target+places/gesture/lesson/related/terms/aliases/doc), 43 topics over the spec's seven
  sections, plus per-place views (`forPlace`, `entry(place,id)`, `identityIndex`, `sizeFor`).
- `HelpGlossary` (15 terms, A–Z helper), `HelpSearch` (pure ranking, GUIDE/TERM/FIX),
  `HelpNavigation` (five screens, back stack, query/scroll/inline term, practice frame).
- All topic and glossary copy in strings.xml; nine orphaned `help_*` strings deleted.
- Compat edits: HelpPresentationModel (targetId-based highlight, place-based colour),
  HelpOverlayView (summaryRes, identityIndex(place,id) — the view's place, never the model's).

## In progress
- Nothing.

## Next
- Phase B (panel) and C (explore) build on the contract in the phase report.

## Gotchas
- Topic ids are not target ids: "sessions" -> hierarchy, "windows" -> windows on Terminal and
  display_apps on Display. `HelpTopics.entry(place, id)` accepts either.
- The overlay must use its own `place` for colours; the model's place lags the first layout pass.
