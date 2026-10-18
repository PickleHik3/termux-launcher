# Performance baselines

The floor every change holds. AGENTS.md ("Memory discipline", "It has to feel right") says why; this
file says how much, and how to tell. A **baseline** is a number measured on pong that a change must
not move the wrong way. A **budget** is a limit the code enforces and a test asserts.

**Pong** is the device of record (Nothing Phone (2) A065, Android 16). Its panel runs at up to
120 Hz, but the system appears to hold apps, this launcher included, at 90 Hz while they are
active and to drop them to 10 Hz at rest. The launcher's working frame budget is therefore
**11.1 ms**. Some older numbers were taken against a 120 Hz budget (8.3 ms), so check which budget a
number assumes before you compare against it. Read the live rate from the device when you measure
(`scripts/perf-baseline.sh` prints the display's active mode), and name it beside any frame
number you write.
Emulator numbers count for memory and start-up trends only. They do not count for
frame time or jank, because `gfxinfo` on ANGLE or lavapipe reports every frame as janky. Ask before
measuring on pong; AGENTS.md's rule on touching a real phone applies.

## The rules

1. **At rest, nothing draws.** A new animation ends. A new clock, sampler or poller slows down in
   lazy mode (`STATS_LAZY_MULTIPLIER`) and stops when nobody is watching. A place that is not on
   screen draws nothing (`707920f71`).
2. **Every pixel has a budget.** Anything that keeps bitmaps, frames or decoded images goes in a
   store with a byte budget. The budget is a constant beside the store's code, and a test asserts
   it. The stores that exist, with their tests as the pattern to copy:

   | Store | Budget | Test |
   |---|---|---|
   | `KittyImageStore` | 32 MB / 256 images stored; frames total RAM ÷ 48, between 64 and 256 MB | `KittyImageStoreTest` |
   | `LauncherIconStore` | heap ÷ 16, between 4 and 16 MB; artwork kept at 192 px | `LauncherIconStoreTest` |
   | `DockIconCache` | heap ÷ 8, between 8 and 32 MB | `DockIconCacheTest` |
   | `WallpaperBlurCache` (the shared frame) | 72 MB, at most 6 radii, about 1 MB per radius at quarter resolution | `WallpaperBlurCacheTest` |

   Work out the cost from the pixels a drawable actually holds (AGENTS.md has the
   `AdaptiveIconDrawable` trap). A store gives memory back on `clear`, when a pane closes and under
   pressure; `ChromePolicy` and `TermuxActivity.onTrimMemory` show which pressure level drops what.
3. **A frame does only the frame's work.** Keep allocation, I/O, decoding and layout requests out
   of `onDraw`, touch handlers and per-frame callbacks. A transition runs one geometry pass
   (`GeometryScheduler`, `KeyboardGeometryChoreographer`, and their tests). Glass samples the shared
   frame, which is blurred once per radius (ADR 0002, 0004). Blurring per surface or per frame is
   what these rules exist to stop.
4. **Heavy work runs off the main thread.** That covers wallpaper decode, blur, file and network I/O
   and package queries. Native model work runs in the `:tai_runtime` process.
5. **Prefer many thin frames to a few fat ones.** On pong, capping the clock or throttling
   the live blur made the median frame worse and tripled jank (`e40f8bc7b`). To get a cost down,
   make each frame cheaper; lowering the frame rate is the wrong fix.
6. **Fail-safe gates.** A gate on drawing carries a backstop (AGENTS.md, "It is the home screen").

## Baselines on pong

| What | Baseline | Where it was measured | How to measure |
|---|---|---|---|
| Idle PSS, live terminal | 203–213 MB, mean 207 MB; native heap about 91 MB (185–200 MB on 2026-09-05) | `scripts/perf-baseline.sh memory`, 1.1.0-pre.1, 2026-10-12 | `dumpsys meminfo <pkg>`, TOTAL PSS, three readings |
| Memory over repeated transitions | keyboard shown and hidden 10 times: native heap +0.7 MB; idle native heap about 108 MB | `c37c48778` | `dumpsys meminfo` before and after the loop |
| Typing in the terminal | frame p50 15 ms, p90 19 ms | `c37c48778` | `terminal.state` → `performance` |
| Heavy terminal output | median frame 5 ms, under 3% janky | `ac27b88a9`, `e40f8bc7b` | `terminal.state` → `performance` |
| Glyph-heavy render, slice 10a | window p95 22 ms; pane render median 2.4 ms; about 336 KB/s allocated, 0 GCs | `project-docs/reference/terminal/kitty-protocol-features.md` | `terminal.state` → `performance` |
| At rest, launcher in front | 5 frames in 30 s (0.17 fps) | `scripts/perf-baseline.sh frames`, 1.1.0-pre.1, 2026-10-12 | `dumpsys gfxinfo <pkg>` over 30 s, untouched |
| At rest, lazy mode on | 1.2 fps, about 2% of a core | `ac27b88a9` | `dumpsys gfxinfo <pkg>` over a minute, plus `top` |
| At rest, lazy mode off | about 21 fps, from the seconds flip (an open item; any change should lower it) | `project-docs/active/perf-home-terminal-idle-handoff-2026-09-27.md` | as above |
| Scripted gestures (`pong.txt` x 3) | 2.4% janky; p50 16 ms, p90 22 ms, p99 44 ms; about 71 frames/s | `scripts/perf-frametime.sh`, 1.1.0-pre.1, 2026-10-12 | `scripts/perf-frametime.sh` |
| Terminal scroll (`pong-scroll.txt` x 3, drags and flings through a 4000-line transcript) | 0.1% janky; p50 8 ms, p90 11–12 ms, p95 13–14 ms, p99 17 ms; about 90 frames/s. Before the row-recording shift (adbf9dd4b): p50 9–11 ms, p90 13–16 ms, p95 14–17 ms, p99 17–19 ms | `scripts/perf-frametime.sh`, 1.1.0-pre.4+dev.c5e1ecccf, 2026-10-15, two runs each | `scripts/perf-frametime.sh scripts/perf-scenarios/pong-scroll.txt` |
| Sliding between places | median 15–16 ms; worst page-change frame 47 ms | `707920f71`, `c6223ec76` | perfetto, `Wall.*` trace sections |
| Pre-roll and settle frames | 30–77 ms now. Target: none above 16.7 ms | `project-docs/active/fancier-glass/SPEC.md` | perfetto |
| Cold start | 1.33–1.54 s from force-stop to first frame, relaunched as home (about 2 s of main thread in an older trace) | `scripts/perf-baseline.sh start`, 1.1.0-pre.1, 2026-10-12 | logcat `Displayed` after `am force-stop`, three runs |
| Release APK, universal | about 100 MB | same | file size |
| On-device AI runtime | 330 MiB resident at rest; E4B on GPU: first token 1.11 s, decode about 11 tok/s | `TaiResidency.java`, `project-docs/reference/voice-ai/gallery-gpu-loading-comparison.md` | `tai benchmark --compare` |

`DEVICE=<serial> PKG=<package> scripts/perf-baseline.sh [memory|frames|start|all]` takes the
memory, frame and start-up rows and prints them as Markdown tables. Run it before and after the
change on the same build type. Start-up reads the logcat `Displayed` time; when the launcher is the
default home app, Android relaunches it cold the moment it is stopped, and that relaunch is what is
timed. Force-stopping ends the terminal sessions, so ask before running `start` on a daily phone.

`DEVICE=<serial> scripts/perf-frametime.sh [scenario]` measures frame times without anyone holding
the phone: it plays a gesture scenario (`scripts/perf-scenarios/pong.txt` by default: page to Home
and back, scroll the terminal) through `adb input` and reads `gfxinfo`. The jank figure is the
missed-deadline count; the percentiles overlap in the pipeline, so compare them between runs, not
against 11.1 ms. A scenario's coordinates match one layout: re-measure them from a screenshot when
the layout or device changes, and add a scenario file for any gesture a change touches.

For terminal rendering, read `performance` from the `terminal.state` action. Pass `{"resetPerformance": true}` first so
that the window, allocation and pane counters all start from zero. The fields are explained in
`project-docs/reference/terminal/kitty-protocol-features.md`. Trace sections (`Trace.beginSection`)
already exist for chrome, frost, accessory geometry, terminal append and the wall slide. Add one for
any new hot path.

The 601 → 171 MB figure in older notes came from a degraded session: 115 apps and a 170-frame GIF.
The RAM work capped how far memory can grow and made it come back. It did not lower the idle floor.
Compare against the idle row above.

## Done

You are done with a change that touches the rules above when your report covers each of these:

- **Rules.** Which rules applied, and what the change does about each.
- **Budget test.** Each new or resized store has its budget constant and a test that asserts it.
- **Baselines.** Each baseline the change could move has a before and an after, measured on the
  same build type in the same state. If you reasoned a number from the code, mark it as reasoned:
  it still owes a device check. A move bigger than the noise in the wrong direction makes the
  change **red**. Stop and report it. Do not tune it away.

Nothing runs these checks for you. There is no benchmark module, CI checks neither APK size nor
lint, and StrictMode is off. The script records numbers; comparing them is your job.
