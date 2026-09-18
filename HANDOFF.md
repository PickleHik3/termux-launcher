# Phase B — the reading panel and the controller

## Done
- `HelpStyle`, `HelpPanelView` (home, search, glossary, topic, inline terms, insets, no keys to
  the terminal), `HelpController` (pages, back order, IME hand-off, practice, explore seam),
  `TermuxActivity` on the controller, phase B strings, `HelpControllerTest` (21 cases).
- Review round: text entry ends before Home/topic/glossary navigation, a fresh invocation puts
  the explorer away, a linked topic's header leads to Help home, neutral missing-topic line.
- `--tests 'com.termux.app.help.*'`: BUILD SUCCESSFUL, 141 tests, 0 failures.

## In progress
- Nothing.

## Next
- Phase D: swap `HelpController.overlayExplorer` for phase C's seam (its javadoc has the code),
  and call `HelpController.onPracticeEnded()` once the tour signals the end of a practice run.

## Gotchas
- D8 deviation: the tour has no end-of-practice signal reachable from the activity, so help
  stays closed after practice; the frame is kept and `onPracticeEnded()` restores it.
- `HelpOverlayView` untouched: phase C owns it. Explore is interim until that branch lands.
