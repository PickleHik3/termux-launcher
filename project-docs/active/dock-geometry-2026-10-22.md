# Dock geometry, Dock rows, Stack layout (#133): built, slide unverified

Spec #133, tickets #134–#137 (closed), ADR 0011. Built on `feat/dock-geometry`, cut from
v2-redesign d0a9a07cd with `fix/round-2026-10-22` merged in; it waits for that round to merge into
v2-redesign. The full unit suite is green at 223b1e7ad. #133 stays open until the slide is seen right
on a device.

## What is built

- **Dock geometry** (`app/.../dock/DockGeometry.java`): one snapshot of both halves in, one
  DockLayout out, down to the per-slot icon. The pinned-apps row and the rail draw only the size
  they are handed.
- **Dock rows** (`dock/DockRows.java`, `DockRowViews.java`): the one writer of the dock's views and
  the one answer to which rows are on screen; readers subscribe.
- **Stack layout** (`place/StackLayout.java`): the bottom stack from one snapshot; a half's rest is
  measured in that half's own Style, Search field, Wall strip and keyboard overlay.

Left out on purpose: Dock rows does not write the keyboard column (hiding it could hide the keyboard
for a frame); the two Robolectric bottom-stack tests stay as view-order tests.

## Open: the slide still arrives in the departing half's dress

On the emulator (2026-10-23, `/home/amalvajdan/reports/dock-geometry-133/`, `ctl-*` = v2-redesign
d0a9a07cd, `chg-*` = this branch), window → Home held at about 70% with `input motionevent`:

- Both builds show the window's row mid-slide: pitch 154 px (Home's is 147.5), no Drawer tile.
  Both snap to Home's row at the settle. The spec's first three user stories are not met there.
- This branch's row sits at y 1943–2040 mid-slide, about 50 px above Home's rest (1993–2098); the
  base's sits at 2029–2134. The branch may have added a vertical jump. Unconfirmed: the branch's
  window had a split keyboard the base's did not.
- At rest, Home and window are identical across the two builds (icons 105 px). A cut-short swipe
  returns the window's dock unchanged in both.

The seam tests pass, so the question is the activity's travel path. Two candidates:

1. The rows never swap halves under an injected hold: `SeamCrossing` stands the other half's rows
   only on some condition (velocity, a frame the emulator skips, the midpoint) the hold never meets.
2. They swap, but the travel frame's snapshot is built with `stackDressPage()` before the swap, so
   the frame measures the departing half.

## Diagnosing it

1. Log per travel frame: wall offset, `mSeamCrossing.stackDressPage()`, the snapshot's `rows` side,
   and the applied DockLayout's `slotIconPx` and `appsRowUsableWidthPx`. Done when one slide each
   way is logged frame by frame and the frame where `rows` changes side (or that it never does) is
   named.
2. Repeat on pong with a real finger, release build. Done when the 118 vs 143 px icon and 1032 vs
   1080 px row from the spec are measured again mid-slide and at rest, with this branch and the
   base.
3. Fix whichever candidate the log shows, with a seam or travel test that goes red on it first.

Owed device checks for all three stages: `project-docs/device-checks.md`, items dated 2026-10-22
tagged #135, #136, #137 and review.
