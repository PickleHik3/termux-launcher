---
status: accepted
date: 2026-11-17
amends: 0012 §1 and §6 (the crossing's work is the pipeline's scenes and writers; "land clears in one step" becomes a guarded rule: the landing writes nothing), 0018 §4 (a crossing lays out nothing: now enforced by a guard and a test, not only stated)
---

# One frame pipeline; a view property has one writer

Spec: [Spec: one writer and one frame order for the launcher's window](https://github.com/PickleHik3/termux-launcher/issues/253), decided on the map [Map: one writer and one frame order for the launcher's window](https://github.com/PickleHik3/termux-launcher/issues/236). Index: [`project-docs/active/window-restructure-2026-11-17.md`](../../project-docs/active/window-restructure-2026-11-17.md).

On 2026-11-17 the launcher's window was drawn by many writers and timed by many clocks. It had 31
pre-draw registrations in 25 files, five owners for the keyboard's numbers, eight glass mechanisms,
six enums naming page kinds and a 35,000-line activity that received every wall event. 397 of 1,163
commits in the last 60 days were fixes. On pong this showed as pops at the landing of a crossing, a
keyboard that snapped up in one frame, and gates that could hold the window's drawing with no
timer behind them. ADRs 0012, 0018 and 0023 had each said "a crossing lays out nothing" and "the
landing writes nothing". Nothing enforced it, so the next feature broke it again.

We decided the following. The ADR states the rules; the build lands them in eight steps.

## 1. One frame order

```
input      record only: touch, wall offset, insets, IME
animation  record only: fling steps, look writes, session events
           STRUCTURE writes (visibility, text, sizes, layout params):
           only when the state's structure changed, each with a short reason
layout     Android
pre-draw   ONE hook, in this order:
             1 snapshot   one immutable state for the frame
             2 scenes     pure
             3 writers    one per layer; pose and material only, never a layout request
             4 glass commit
draw       Android
```

Structure is written before layout, pose and material in the one pre-draw hook. A scene reads
geometry laid out in the same frame, and glass is recorded after everything beneath it is placed.
The pipeline runs only on a frame an input marked dirty, so nothing repaints at rest. Inputs only
record state and ask for a frame. A content change during a crossing (a clock tick, a chip, a
badge) is applied at the structure point and may lay out; it is counted, not flagged (ADR 0023 §4 stands).

## 2. One writer per view property

Each layer has one writer, and only that writer sets the properties it owns. The Appearance editor,
the activity and every page are never second writers (section 8).

## 3. Who may hook a frame

Only the pipeline registers a pre-draw listener or frame callback on the window. Code that touches a
view a scene owns joins as a named step in one ordered table in one file. A sheet, popup or widget
that only waits for its own first layout uses one shared helper and nothing else. Of today's 31
registrations about 11 become pipeline steps or Holds; the other 20 move onto the helper.

Nothing writes outside a frame. Where a result is needed before the next frame (the recents
snapshot, pause, a test), the caller asks for a named flush that runs the whole table once, in
order, and logs who asked. `ChromeRenderer.commitNow` goes.

## 4. A Hold is state

A wait on something Android or another process delivers late is a named Hold in the state. The
scene keeps the waiting element at its before-pose, the rest of the window keeps moving, and the
release is the next frame's input. Every release logs its reason: signal, landing or backstop.
Nothing in the pipeline cancels a draw, with one exception: the window's first frame after a
relaunch may be held, with frame-count and time backstops. Where two things must appear on the same
frame, both read the same Hold.

## 5. Three guards and the counters

While the motion log is on, every layer's writer runs three guards, which log and never crash:

- **foreign writer**: a property the writer owns was changed by someone else since its last write;
- **landing wrote**: the landing frame changed anything, so the scene at progress 1 was not the
  arriving rest;
- **crossing laid out**: a layout request came from the pose writers or was caused by the crossing.

Each guard and each Hold keeps a count, first and last time and last detail in a small table that
outlives the motion log's ring and is dumped with it. The counters went onto pong's diagnostic build
first, so the order of work came from numbers.

## 6. The window's layers

The layer number is the only Z between layers. Inside a layer the order is the table's child order.
Elevation and translationZ draw shadows and never reorder. `bringToFront` goes. Runtime views join
their layer's host inside the root; nothing is added to the window decor or beside the root.
Source: [The window's layers on paper](https://github.com/PickleHik3/termux-launcher/issues/246).

| Layer | Holds |
|---|---|
| 0 ground | wallpaper, the live scene below the window, the X11 display surface, the keyboard's frost over it |
| 1 pages | Home pages (widgets, glyph row, floats), windows (panes, the X11 frame, the Assistant) |
| 2 chrome | top stack (status bar, window chips), bottom stack (slab, rows, keyboard), A–Z tab, Home pill, page switch panel, find bar, rename chip |
| 3 sheets | drawer, palette, terminal sheet, Home and terminal action sheets, menus |
| 4 editor | the Appearance editor's frame, tap layer and card sheet |
| 5 notices | notices, snackbars, help and tour |

The glass service commits back to front in this order. Anything that relied on drawing over the
system bars is checked on pong.

## 7. Packages and Hosts

Three new packages: `window` (the pipeline table, snapshot, Holds, guards, flush, the first-layout
helper, the layer table), `glass` (the glass service) and `page` (Page, PageKind, Side). Each
element's scene and writer stay in the element's package (the bottom stack in `dock`, the top stack
in `statusbar`). Imports flow elements, then `page`, then `glass`, then `window`. `window` imports
nothing app-level, and nothing imports the activity, which wires everything and is the only place
that knows everything.

One source-scan test enforces all of it: no frame hook outside the pipeline and its helper, the
import direction, no `bringToFront`, no Z used for ordering and no `addView` to the decor or beside
the root outside a short allow-list, and no Host over 15 methods. Today's cycles, activity imports
and big Hosts sit on allow-lists that may only shrink; each step deletes the lines it fixes. The new
packages have no exceptions. A Host names only what its module needs. A Page gets a small PageHost
from `window`, never the activity.
Source: [Package boundaries: who may import whom, and how narrow a Host is](https://github.com/PickleHik3/termux-launcher/issues/252).

## 8. Holds and gates: what goes, what stays, what moves

Decided in [Holds: which waits are real and what each shows while waiting](https://github.com/PickleHik3/termux-launcher/issues/250)
and confirmed by [the counters on pong](https://github.com/PickleHik3/termux-launcher/issues/249),
a 7.7-minute power-user session on the diagnostic build.

| Hold or gate | Decision | Evidence or reason |
|---|---|---|
| Geometry scheduler's async pass | delete | structure is written before layout (3 of 140 ended on the backstop) |
| Return-resize hold | delete | resume never resizes unless the page's rest changed (one resume resize after a crossing was counted) |
| Keyboard reveal gate and close correction | delete | the keyboard shows on the frame it is asked for ([ADR 0030](0030-pages-sides-and-the-bottom-stack.md)) |
| Chrome commit's draw cancel | delete | nothing cancels a draw (a third of 450 commits were caught at pre-draw, 151 ended on the backstop) |
| Apps row morph settle and stable-bounds waits | delete | the crossing morph goes; both sides' rows stay laid out (11 of 29 waits ended on the backstop) |
| Appearance editor commit hold | delete | Done writes the draft once |
| Display focus retry | delete | focus is one-time work at the structure point (24 of 27 ended on the backstop) |
| X11 surface first frame, X11 host size settling | keep, named Hold | waits on another process |
| Wallpaper colours callback, window-foreground resolver | keep, named Hold | waits on a system service or IPC |
| Wall first-draw gate, Display touchpad layout wait | the first-layout helper | a local wait for its own first draw |
| Chrome first-frame gate and relaunch reveal | the one draw-holding exception | merged; keeps frame-count and time backstops |

While waiting, a kept Hold keeps its element at its before-pose. The four zero-count lines in the
session are all keep, helper or exception lines, so no deletion rests on them.

## 9. The Appearance editor is never a writer

The editor previews through one draft layered over the store. The frame snapshot reads the store
with the draft on top, so scenes and writers show the preview, and the editor writes no element.
Done writes the draft once; Discard drops it, so nothing is restored and the on-disk entry store
goes. Its direct writes to launcher views (group alpha, scale, translation, background, the frame
scaling the live launcher) become editor state in the snapshot, applied by each layer's writer; the
editor draws only its own frame outline, halo and clock-face tiles. Dirty is the draft against the
store, so setting a value back clears the unsaved dot, and a write from `launcherctl` or Settings
survives Done and Discard; for a cell both touched, the draft wins until Done. The draft lands
first, each layer's step then removes the editor's writes for that layer.
Source: [How the Appearance editor reaches what it edits](https://github.com/PickleHik3/termux-launcher/issues/245).

## Considered options

- **Move the wall out of the terminal's column and re-layer the window.** Rejected for now: a later
  effort; this keeps the view tree's shape.
- **A shared clock for glass alone.** Tried on pong ([Prototype: one glass clock for the pane slabs and the dock](https://github.com/PickleHik3/termux-launcher/issues/241)):
  it removed no visible pop. The pops were picture swaps, which [ADR 0029](0029-each-glass-surface-keeps-one-picture.md) removes.
- **Guards that crash.** Rejected: this is the home screen; a guard logs and counts.
- **Keeping every Hold with a better backstop.** Rejected: the counters show waits that only ever
  end on their backstop. The design removes their cause.

## Consequences

- Hard to reverse: every writer, every pre-draw registration and every Host moves behind the
  pipeline and the new packages.
- The pipeline's table is the one place to read the window's order; a new feature joins it as a step.
- Today's cycles stay until their step deletes the allow-list lines; the new packages start clean.
- Release bar: 2.0 stable only after a day of normal use on pong with every guard and counter at
  zero (no foreign writer, no landing write, no crossing layout or resize, no unexpected backstop).
