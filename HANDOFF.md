# Phase B — the reading panel and the controller

## Done
- `HelpStyle`, `HelpPanelView` (home, search, glossary, topic, inline terms, insets, no keys to
  the terminal), `HelpController` (pages, back order, IME hand-off, practice, explore seam),
  `TermuxActivity` on the controller, phase B strings, `HelpControllerTest` (16 cases).
- `--tests 'com.termux.app.help.*'`: BUILD SUCCESSFUL, 136 tests, 0 failures.

## In progress
- Nothing.

## Next
- Phase D: swap `HelpController.overlayExplorer` for phase C's seam (its javadoc has the code),
  and call `HelpController.onPracticeEnded()` once the tour signals the end of a practice run.

## Gotchas
- D8 deviation: the tour has no end-of-practice signal reachable from the activity, so help
  stays closed after practice; the frame is kept and `onPracticeEnded()` restores it.
- `HelpOverlayView` untouched: phase C owns it. Explore is interim until that branch lands.
