# Main-thread offload, 2026-10-06

Branch `perf/main-thread-offload` (from `dev` 5f18dc178). Source: the main-thread audit of 2026-10-06
(five read-only sweeps: startup, draw, touch/layout, launcher side, terminal output; two more on what
can leave the main thread). The rule for this branch: **nothing a user can see changes.** Same pixels,
same behaviour, same timing of visible events. Items that would change something visible are held
below for a decision, not quietly dropped.

## Queued (five worker branches, merged here)

`perf/mt-activity` — TermuxActivity and its helpers
- wallpaper source facts (system wallpaper id, managed file presence and size) held in one snapshot,
  rebuilt on real change; no binder or stat per chrome pass, tap or editor frame
- slide frame: views cached, glass aimed once, pane borders recomputed on off-screen change only
- insets dispatch: blur effect, rim drawable and outline provider cached by key
- catalogue signature computed off main; default-home answer cached; recents policy once per pass
- title change goes through the 250 ms window-bar schedule; drawer rebuilt only when a label changed
- attention glow paused while the Terminal place is off screen (it is at alpha 0)
- Linux app tap resolves from the catalogue snapshot, or off main
- size changes no longer reload the tap-correction model
- per-touch reads cached (reduced-motion setting, border insets, view lookups)

`perf/mt-draw` — per-frame allocation and repaint, pixel-identical
- status bar lens, clock, cursor trail, dock glow and ripple, pinned cards, A–Z fx, page ticks:
  gradients and text measures cached, no allocation in onDraw
- refraction program kept across crossfades instead of recompiled
- RealtimeBlurView preDraw early-out when hidden
- keyboard hint breath invalidates only the lit keys (recorded in inapp-keyboard/UPSTREAM.md)

`perf/mt-terminal` — output path
- shell output drained in 16 KB slices with exit ordering preserved
- URL underlines rescan only changed row groups (same underlines)
- accessibility text gated on touch exploration and throttled; TalkBack unchanged
- per-line HashSet, refresh-rate and OSC prefix allocations removed
- font config memoised by file stamps; export-only palettes built off main

`perf/mt-launcher` — launcher side
- pinned notification rebuild debounced (≤150 ms) on a background thread; rules cached; media compared by value
- stats sampled on a background thread
- one process-wide, thread-safe icon-pack repository; override drawables cached
- icon overrides parsed once per snapshot; one in-flight catalogue load
- launch tries the explicit component first; `am` fallback off main
- files shared into Termux copied off main behind a progress dialog

`perf/mt-startup` — startup, prefs, editor, AI client
- installers once per process, off main where nothing waits on them; cli scripts content-checked
- preferences handle construction cached; crash-log check stats first
- Look presets written in one editor; look labels restyled only on stop change
- tap-correction reload race fixed
- AI client parses replies on its own looper, delivers on main
- speech model resolution and read-aloud availability cached

## Held: would change something visible (decision needed)

- DONE 2026-10-06 (second round): **geometry scheduler** (one pass per transition; the fold still relayouts per frame but runs no chrome pass until it lands; translation+clip for the fold is a follow-up), **Look slider overlay**, **async icons with a 180 ms fade**, plus the editor preview area and distinct cursor trails. Originally held because it changes
  when the terminal reflows and how the fold clips. The biggest felt win; needs the grilling loop.
- **Live-preview pipeline** (Look slider deferred to release, dock size through the drag gate): the Look
  stop would stop previewing mid-drag unless an in-memory overlay is built first.
- **Managed backdrop crop deferred** (FrameCapture.deferred): one frame without the managed backdrop on
  cold start and wallpaper change, as the system-wallpaper path already shows.
- **Async icons with placeholders** in the drawer and dock: icons would pop in.
- **Bell / OSC 99 rate limit**: fewer sounds and notices per burst.
- **A–Z scrub weight quantised** to five cached typefaces: visible weight steps.
- **URL underlines after a quiet 150 ms**: superseded by the per-row cache above (no change).
- **commit() → apply()** for dock and widget edits: companion apps in the shared user may read the files.
- **Settings search index off main**: custom Preference constructors may touch views.
- **Model / speech / style settings screens** loading off main: "Loading…" summaries would show.
- **Widget host reconcile off main**: AppWidgetHost binder ordering.
- **Voice recorder opened on the capture thread**: start() becomes async; the fallback moves.
- **Wallpaper picture read off main**: the re-dress must order after the answer.
- **Kitty transfers and placement index**: upstream-shaped; a larger change.
- Everything about living stills and the generated wallpaper host: removed in d6bbc90d8.

## Verification

Unit suites per module in this session; Waydroid for visual equivalence; trace counts (passes per
keyboard transition, binder calls per apply) on the HTC hub; pong only for the final feel, when asked.
