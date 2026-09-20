Done: main code for phase 1 — WELCOME card kind, RUN_VERSION 4, isOffered(),
takeTheTour/notNow, v3 resume mapping, closing card shell + 3 sections, strings.
Compiles (:app:compileDebugJavaWithJavac).
In progress: unit tests — existing ones assume start() shows find_help and 4 closing sections.
Next: update TourControllerTest/TourRunTest/TourClosingCardTest, add the new cases,
run :app:testDebugUnitTest, delete this file, commit.
Gotchas: welcome is NOT in TourRun.steps() — lesson indexes unchanged on purpose;
setSteps is allowed while the welcome card is up.
