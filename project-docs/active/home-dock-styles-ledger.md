# Home dock styles: ledger

Rolling status for [`home-dock-styles-2026-10-20.md`](home-dock-styles-2026-10-20.md). Newest
first within each section. Delete this file with the spec when the work is delivered.

## Status

| Step | State | Commit | Notes |
|---|---|---|---|
| 1 Dock style pref + picker | merged, unchecked on device | f8ed2e66b | Picker in the Layout editor's Row B while Home's dock is selected; `DockStyle.BUILT` gates unbuilt styles to Glass tray. |
| 2 Glass tray + shared glass | merged, unchecked on device | e2da84a2e | Shelf, soft rim, quarter-size icon reflection captured on change. Open: 9 dp slab jump at the Home/Workspace settle; reflection pops off/on during page swipes; no dash (cue) with one dock page. |
| 3 Lens beads | merged, unchecked on device | (merge below) | One glass program draws every bead (nearest-bead SDF from uniforms, no per-bead RenderNode); the host clips its children to the beads, so below Android 13 they are plain frost. Minimised Home float stays today's chip (not a bead yet). |
| Emulator pass 1 | not started | | |
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
