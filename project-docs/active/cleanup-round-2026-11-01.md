# Cleanup round: holds, watchdogs, dual mechanisms and globals

Grilled 2026-11-01. Findings and anchors are in `code-health-review-2026-11-01.md` (the review);
this file is the plan. Terms: **Hold**, **Backstop**, **Gate** (`docs/GLOSSARY.md`, Look and
motion).

## Scope and gate

- Owns the review's sections 1–5 and 7 as waves. Section 6 (thread hazards) is one umbrella
  issue, each item reproduced before it is fixed. Sections 8 (swallowed exceptions) and 9 (the
  activity's extractions) wait for their own round.
- Lands on `v2-redesign`. W1 starts after ADR 0018's last wave (R) merges; the widgets and
  terminal-rows checks and the home-conventions code work may overlap, since they touch other
  files.
- Every wave but W3's watchdog removals is behaviour-neutral. Merge bar per wave: the unit suite
  green, `project-docs/verification/motion-invariants.sh` clean on the emulator, and the hold
  sweep's motion log identical before and after the wave in holds and release reasons (timings
  ignored). W1 itself is measured against suite and invariants only, since the log it adds has
  no "before".

## The decisions

1. **Instrument first, convert second.** W1 changes no structure: each of the thirteen existing
   backstop runnables gets one line, and the keyboard show chain one per retry. The rewrite of
   the sites onto the `Hold` primitive comes in W3, after the logs are read, one seam per worker,
   so a hold about to be deleted is never rewritten.
2. **Two pong days plus an emulator hold sweep**, not a week. The sweep is one scenario per
   hold under `scripts/perf-scenarios/emu-hold-*.txt` (keyboard open and close, Home to Terminal
   and back, display open and close, drawer open, widget resize, divider drag, resume from another
   app), each run twenty times with the motion log on. Pong gives the real layouts and 90 Hz; the
   sweep gives coverage the two days cannot promise. Both logs are read before any watchdog
   decision.
3. **The fired rule.** A backstop that never fired in either log has its watchdog removed
   outright. One that fired becomes a ticket carrying the log lines around it, and its watchdog
   stays until the ticket finds the missing signal. A firing is a bug with a name, never proof
   that the watchdog is right.
4. **Log form.** A firing writes `backstop <name> <waited ms>` to the motion log and bumps a
   per-name counter; `launcherctl motion-log dump` prints `backstops: <name>=<count>` after
   `invariants`, so a wrapped ring buffer still answers "did it ever fire". Counters are zero
   cost when the log is off (one boolean check, no allocation), like the rest of `MotionLog`.
5. **Hold's scope.** `com.termux.app.motion.Hold`: a named wait released by one signal, N
   frames, or a backstop in ms; generation-checked so a superseded hold is a no-op; `release(reason)`
   logs the reason. Quiet-period settles (X11PaneFrame's 48 ms height-still, PeekGlassReflection's
   160 ms still) are debounces, not holds: W2 replaces them by asking the mover for its last pass.
   WidgetGridView's 160 ms size delivery stays a debounce, because its mover is the finger.
   `MotionLog` moves to `app/motion` in its own later commit.
6. **One reload owner.** `PendingReload`, held by `TermuxApplication`: `request(recreate)`,
   `consume()`, and a listener a live activity registers in `onStart` and drops in `onStop`. The
   style and drawer reload broadcasts, their receiver and the three statics go; "a recreate owed
   stays owed" becomes a field of the request.
7. **Page-scoped preferences.** `TermuxAppSharedPreferences.forPage(pageId)` returns a view
   object the activity hands to whatever draws that page; getters keep their signatures and read
   the view's page. `sTerminalPageId` is removed.
8. **Rules as a test.** One architecture test in `app/src/test` scans `app/src/main` and fails on:
   `removeCallbacksAndMessages(null)`; more than one main-looper `Handler` per class; a
   `postDelayed` with a literal millisecond count; `System.currentTimeMillis` and
   `SystemClock.uptimeMillis` in one class. Each rule has an allowlist seeded at today's counts
   that may only shrink; a line that grows fails the suite.
9. **Hazards as one issue.** The nine section-6 items are one umbrella issue with file:line and
   the reproduction owed for each; items are struck through as they close.

## Allowlist seeds (at `4ebbf4449`)

- `removeCallbacksAndMessages(null)`: TermuxActivity 8; X11LinuxAppRunner, X11GpuBenchmarkRun,
  PageOverviewView, SetupShellRun, HomePinRequestActivity, TaiBenchCheckSheet 2 each.
- Main-looper handlers, 2+: TermuxActivity 10; TermuxInAppKeyboard 3;
  BackupRestorePreferencesFragment 3; X11PaneFrame, TermuxTerminalSessionActivityClient,
  TerminalActionDispatcher, SafeLauncherAppWidgetHostView, TaiModelCentreFragment,
  GuiAppsSetupGuideFragment, WallpaperPickerPage 2 each.
- `postDelayed` with a literal: 11 sites in 6 files.
- Two clocks in one class: 5 files.

## Waves

| Wave | Delivers | Gate |
|---|---|---|
| W0 | this spec, the review, three glossary entries, the umbrella issue | now |
| W1 | `MotionLog` backstop line and counters, the dump's `backstops:` row; one line in each of the thirteen backstop runnables (review §1) and one per retry in `TermuxTerminalViewClient.setSoftKeyboardState` saying whether the IME inset was already up; the architecture test with its seeds; the `emu-hold-*` scenarios | ADR 0018 wave R merged |
| pong | W1 staging build on pong, Motion log on, two days; the sweep on the emulator meanwhile | W1 merged |
| W2 | review §3 deletions (the dead foreground deferral, the root view's clocks); the handler rule applied to TermuxActivity (one handler, cancel by runnable); `PendingReload`; the timer-plus-clock duplicates (§4); the foreground label's cadence owned by `WindowForegroundResolver`; X11PaneFrame and PeekGlassReflection ask their mover | during the pong days |
| W3 | the fired rule applied to each watchdog (blur heartbeat, keyboard show chain, keys safety net, dock deferred render, touchpad wait); `Hold` and the conversion of surviving holds, one seam per worker; keyboard show on `onWindowFocusChanged` plus one insets-dispatch check; keys handed back inside `DisplayStopSequence`; tickets for the backstops that fired | both logs read |
| W4 | `forPage` view and the static's removal; `LiveSceneFrame` as an instance the scene controller owns; `sFirstShellStarted`, `sProcessPaneLayout`, `sLiveActivity`, `sHomeWallpaperInPlace` into owners; `LauncherSchemeTheme` cache guarded or instanced | W3 merged |
| W5 | review §7: the stack margin asked from `StackLayout`; the 0–40 clamp into `DockLayoutPolicy`; the repeated literals named once each | W4 merged |

Each wave is one worker per seam, cut from the previous wave's merged commit, with the review's
anchors in its brief and the merge bar above as its report format. The W1 build goes on pong only
when the developer says so.

## Owed

- Pong: W1 build installed, Motion log on; two days; `launcherctl motion-log dump` read.
- Emulator: the hold sweep run twenty times per scenario; log kept beside the pong dump.
- The umbrella issue number, once filed, goes here.
