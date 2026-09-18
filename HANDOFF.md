# Phase B — the reading panel and the controller

## Done
- `HelpStyle` (dress + accent + view shapes), `HelpPanelView` (home, search, glossary, topic,
  inline terms, insets, no keys to the terminal), `HelpController` (navigation, back order, IME
  hand-off, practice, explore seam), TermuxActivity wired to the controller, phase B strings.
- `:app:compileDebugJavaWithJavac` green.

## In progress
- `HelpControllerTest` (Robolectric).

## Next
- Run `--tests 'com.termux.app.help.*'`; report.

## Gotchas
- `HelpController.overlayExplorer` is the B–C bridge: it drives today's `HelpOverlayView`; the
  seam version is written out in its javadoc, swap it when phase C lands.
- D8: no end-of-practice signal exists in the tour; `onPracticeEnded()` is there, uncalled.
