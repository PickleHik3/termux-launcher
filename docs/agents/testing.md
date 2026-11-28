# Testing and device checks

## Unit and layout tests

- JUnit 4, Robolectric where Android behaviour is needed, AndroidX Test for instrumentation.
  Tests sit beside their module in `src/test/java` / `src/androidTest/java`, mirror the
  production package, and end in `Test`.
- Robolectric layout tests use `@GraphicsMode(GraphicsMode.Mode.NATIVE)` so text has real
  metrics; a layout assertion in legacy mode proves nothing.
- The full suite is green (thousands of tests). If failures appear, compare the failing test
  *name lists* against a clean worktree, never the counts.
- `TerminalIOPreferencesDataStoreLazyModeTest` is a known order-dependent flake (a static
  singleton in `TerminalIOPreferencesFragment`); it passes alone.

## The emulator

The emulator is for screenshots and drivable checks; pong is the truth for layout and motion.
Use the host's android-control skill (`ad`) rather than re-deriving the setup. What must hold:

- An AVD such as `tl16`, with a GPU mode that survives the app's blur (swiftshader segfaults).
- Install the **x86_64** split. Always pass `-s emulator-5554`: if qemu dies, adb silently falls
  back to the phone.
- This launcher becomes the emulator's home screen when installed. Set the stock launcher back
  as home before you hand the emulator over.
- Simulate other phones with `wm size` / `wm density` / `font_scale`, and force-stop the app
  after a density or font change, or the layout keeps the old dp scale.
- Swap checks (stable → drop → stable) need release builds of both; they install over each other
  because every build shares one signing key.
- When you are done, force-stop the app and release the lease (which stops an emulator it
  started), and stop any background monitor or logcat loop.

What the emulator cannot tell you:

- **Jank.** `gfxinfo` on a software renderer reports every frame as janky. Judge motion on pong.
- **Dialogs.** A UI-bounds dump does not see dialog windows; screenshot and tap fresh coordinates.
- **Microphone.** There is none; voice checks need pong.

## Pong rounds

Until the next release ships (the developer set this on 2026-11-18):

- Every build installed on pong is a logging build. The motion log stays on, and the diagnostic
  lines a wave adds go into pong's APK. A diag commit is reverted only on the branch that merges,
  after pong's APK is built from a head that still has it; say which diag commits the APK carries.
- A defect seen on pong is chased on pong's log, not reproduced on the emulator first. The
  emulator sweeps the other setups (Floating, other layouts, keyboard states) for regressions.
- One recorded round is `scripts/pong-round.sh <outdir>`: both Home ⇄ Terminal crossings at every
  frame, the motion log per crossing, a frame scan and strips of the biggest jumps
  (`summary.txt`). It runs only when the developer has said they are off the phone.
- The developer's own use is evidence too: `ssh termux launcherctl motion-log dump` reads it
  with no taps or screenshots, so it may run while they use the phone.
- Owed tap checks for a build (`project-docs/device-checks.md`) go into the same round, one
  `device-checker` batch right after the recording, not separate rounds.

## Owed checks

Say which items you verified, on what, and which are reasoned from the code. Whatever still owes
a device check lands in the same commit:

- an item an agent can drive over adb goes on `project-docs/device-checks.md`;
- one that needs the developer's hand (feel, a thumb mid-gesture that adb cannot reproduce, a lock
  screen, taste) goes on `project-docs/developer-checks.md`.

Frame timing and Perfetto traces of scripted gestures are agent work: `scripts/perf-frametime.sh`
and the `device-checker` play the swipes with `adb shell input`, and a Perfetto trace is recorded
over adb while they run (the launcher's `Wall.*` and `LiveBackdrop.*` sections name the steps).
On pong, ask the developer before every such run: it drives their phone.

A pong round for Home ⇄ Terminal motion is `scripts/pong-round.sh <outdir> [--start home1|home2]`.
It leases pong, refuses unless the launcher is in front, records both crossings at every real
frame, dumps and clears the motion log per crossing, and writes `summary.txt`: the build, the
landings, the guard and INVARIANT counters, and the jumps in band brightness that stand alone
(the swipe's own motion is a run of jumps), with a strip of frames around each. Look at the strips
before calling a round clean. `RESCAN=1` re-scans a filled outdir without the device.

Delete an item once it has been seen. Batched functional checks run through the host's
`device-checker` agent where there is one; it gets the user-facing summary and the items, never
the diff.

## Picture reports

Tests whose only job is to write pictures for a person to look at are skipped in an ordinary run
(`ReportRenders.assumeRequested()`). Run them with `-Preports.renders`, for example
`./gradlew :app:testDebugUnitTest --tests 'com.termux.app.surfaces.*' -Preports.renders`.
They write PNGs under `app/build/reports/editor-rows` (card sheets), `editor-row-kinds` and
`tile-grid`, and under `app/src/test/screenshots` (the Roborazzi prototype in
`ScreenshotPrototypeTest`). A new picture-only test calls `ReportRenders.assumeRequested()` first;
a test that also asserts something stays ungated.
