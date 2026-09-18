# Phase A — the help model (branch feat/help-model)

## Done
- `HelpTopics` rewritten: global topic record (group/kind/summary/action/steps/wayBack/reveal/
  target+places/gesture/lesson/related/terms/aliases/doc), 43 topics, per-place compat views.
- `HelpGlossary` (15 terms), `HelpSearch` (pure ranking), `HelpNavigation` (screens + back stack).
- All topic/glossary copy in strings.xml; nine orphaned help_* strings deleted.
- Compat edits in HelpPresentationModel and HelpOverlayView (summaryRes, identityIndex(place,id)).

## In progress
- First compile/test run of `:app:testDebugUnitTest --tests 'com.termux.app.help.*'`.

## Next
- Fix whatever the run reports, commit, write the contract for phases B and C.

## Gotchas
- Topic ids are not target ids: target "sessions" -> topic "hierarchy", "windows" -> "windows" on
  Terminal but "display_apps" on Display. `HelpTopics.entry(place, id)` takes either.
- `HelpTestText` reads the real strings.xml off disk, so tests rank the actual copy.
