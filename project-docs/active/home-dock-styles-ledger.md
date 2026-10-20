# Home dock styles: ledger

Rolling status for [`home-dock-styles-2026-10-20.md`](home-dock-styles-2026-10-20.md). Newest
first within each section. Delete this file with the spec when the work is delivered.

## Status

| Step | State | Commit | Notes |
|---|---|---|---|
| 1 Dock style pref + picker | merged, unchecked on device | f8ed2e66b | Picker in the Layout editor's Row B while Home's dock is selected; `DockStyle.BUILT` gates unbuilt styles to Glass tray. |
| 2 Glass tray + shared glass | merged, unchecked on device | e2da84a2e | Shelf, soft rim, quarter-size icon reflection captured on change. Open: 9 dp slab jump at the Home/Workspace settle; reflection pops off/on during page swipes; no dash (cue) with one dock page. |
| 3 Lens beads | merged, unchecked on device | eae04e066 | One glass program draws every bead (nearest-bead SDF from uniforms, no per-bead RenderNode); the host clips its children to the beads, so below Android 13 they are plain frost. Minimised Home float stays today's chip (not a bead yet). |
| Emulator pass 1 | done; polish round merged (beads ride their icons, reflection fades, one cue, editor Undo fixed, drawer regions), unchecked on device | 8c9ea69f9 | Pass: picker (Row B, font 1.3 tight), Undo/persist, tray padding, no slab jump at the seam, live tray⇄beads, smooth bead edges, no shader/capture errors. Fix: beads stay put while icons slide on a page swipe; dashes stacked on the pull handle; end beads touch the screen edge; drawer could not be opened under injection (cause unknown); editor pill shifts when Undo appears. | | |
| 4a Taskbar + run strip | not started | | |
| 4b Prompt line | not started | | |
| 4c Wall strip | not started | | |
| Emulator pass 2 | not started | | |
| 5 Pong install | not started | | |

## Side fixes

| Fix | State | Commit |
|---|---|---|
| Drawer: pull down on Suggested's empty pane closed the drawer, now closes the folder | merged, unchecked on device | ba6144443 |

## Device checks

Owed checks also go into [`../device-checks.md`](../device-checks.md) in its usual form; this list
records what each pass found.

## Decisions taken without the developer

The developer delegated decisions for this run (2026-10-20, overnight). Each is recorded here with
its reason so it can be reversed.

- 2026-10-20 · The picker lives in the Layout editor's Row B, shown while Home's dock is selected
  (as the keyboard's tools are), rather than as a new settings row: it keeps the fragile settings
  pages untouched and puts the choice next to the dock it changes. Its name, "Dock style", sits
  near the older Docked/Floating "Style"; revisit if the two read as one.
- 2026-10-20 · Living glass does not tilt the dock, so the dock's icon reflection does not shift
  with tilt: wiring it would rebuild the dock's effect every frame of a press. Kept static.
- 2026-10-20 · Lens beads fall back to the tray's slab where the apps row is not alone on the
  dock's glass (the A–Z row on the dock, the keyboard sharing the glass, a joined Docked frame)
  or holds more than eight icons: the beads are one glass program over the row, and another band
  on the same glass would lose its glass.
- 2026-10-20 · A minimised Home float stays today's chip above the dock under Lens beads rather
  than a sixth bead at the row's end: the row's slots are the pinned apps' own, and a bead for
  the float needs a slot the row does not have. Revisit with the Taskbar's run strip.
- 2026-10-20 · Pass 1 fixes. Lens beads ride under their icons during a dock page swipe (and
  the rarer slide-and-fade switch, where they shrink with the fade) rather than easing into the
  slab: a bead carried with its icon reads as one physical lens, and a morph to the slab would
  cost the same per-frame work plus a shape change. Each frame of a swipe rebuilds the dock's
  effect once; the dock's chain has no blur of its own (the frost is pre-blurred), so that is one
  pass of the glass program over the dock, and the shadows' paints are kept. Nothing runs at
  rest. The program reads twelve beads so the neighbouring page's fit beside a full row.
- 2026-10-20 · Not done: beads kept 12 dp from the screen edges. At the default seven icons a
  page on a 411 dp phone the row's slots are 154 px apart with the first centre 77 px in; a 12 dp
  (31.5 px) margin leaves a 91 px bead under a 120 px icon (0.76x), under the 1.08x floor, so every
  default install would fall back to the slab. Doing it needs the row itself inset under Lens
  beads (about 9 dp a side fits seven at about 1.1x with 12 dp margins); the developer's call.
- 2026-10-20 · Home's grabber pill is not drawn while the dock's page dashes show (two or more
  pages): the dashes are the cue then, as the spec says. Its swipe zone is unchanged.
- 2026-10-20 · Lens beads: the apps row gets about 9 dp more side inset so seven beads fit at
  ~1.1x with 12 dp from the screen edges; without it every default install (7 icons on 411 dp)
  would fall back to the slab.
- 2026-10-20 · The drawer opening from the dock needs half its travel or a fast fling; a slow
  real-finger pull on Home cannot commit because Home's dock leaves too little screen below it.
  Pre-existing, not from the dock work; left for the developer to judge (noted, not changed).
- 2026-10-20 · Lens beads rebuild the dock's effect once per frame during a dock page swipe so the
  beads ride their icons (one shader pass over the already-blurred frost, nothing at rest). To be
  judged for jank on pong.
