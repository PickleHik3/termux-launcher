# tour key-row card
Goal: the "New key row" offer becomes a card of the first-launch run, before the keyboard lesson.
Done: TourStep.imageRes, TourAction SWITCH/KEEP_KEY_ROW, TourRun.KEY_ROW between find_apps and
keyboard (passed over when there is nothing to ask), TourController Choice + onTourKeyRowChoice +
dropStep, FirstBootTour.KeyRowHost, TourOverlayView picture, TermuxActivity host, strings.
Next: tests (TourRunTest, TourControllerTest, ExtraKeysDefaultOfferTest).
Acceptance: ./gradlew :app:testDebugUnitTest --tests 'com.termux.app.tour.*'
  --tests 'com.termux.app.terminal.io.*' -q ; ./gradlew :app:compileDebugJavaWithJavac -q
Branch feat/tour-key-row, cut from dev da1f3321. Do not merge to dev.
