---
status: accepted
date: 2026-10-22
amends: 0009's "nothing resizes on the swipe", by naming who guarantees it
---

# One owner for the dock's measurements and dressing, measured from a snapshot of both halves

ADR 0009 made the dock one dock across the wall: one set of rows, one height, and sizes linked to
the shared layout, so nothing resizes on the swipe. In practice `TermuxActivity` assembled the
dock's measurements and dressing from its own fields, per place and per travel frame, in several
passes that could disagree, and the pinned-apps row sized itself when the dock had not spoken. A
slide drew the pinned icons at the departing half's size (118 px against 143 px at rest) and grew
them at the settle; widgets jumped when Home carried a Search field; icons shifted when it carried
a Drawer tile; a card was drawn under Home's beads and then taken away; a window not shown during
the slide came back with a 213 px wallpaper gap under its pane. Four fixes went in reader by reader,
and nothing tested the assembly, so the next reader would bite the same way.

We decided that the dock's measurements and dressing have one owner, and that a slide is an input to
it, never a path of its own.

1. **Dock geometry.** A pure module takes one snapshot of both halves (each half's layout, **Dock
   design**, **Search field** and **Drawer tile**, which half's rows the frame shows, keyboard,
   device facts, the live eases) and answers with one DockLayout, down to the final per-slot icon.
   The existing dock layout policy is its internal implementation, its arithmetic unchanged.
2. **Dock rows.** One adapter is the only code that touches the accessory views. Handed the
   applied DockLayout, it applies presence, heights and glass layers, and announces which Elements
   are on screen; the icon reflection, drag cancellation and page ticks subscribe to that instead of
   reading view state.
3. **Stack layout.** A pure module takes one snapshot and answers with the whole bottom stack
   (heights, margins, overlap, reservation, and the DockLayout from Dock geometry). Travel asks it
   for a half's rest rather than deriving the stack a second way.

They land in that order, each as its own stage. The activity gathers the snapshot, keeps one
applied DockLayout per pass, writes the results and does no dock arithmetic. The rows a frame shows
are measured and dressed as their own half from the first frame, and a frame with no size draws no
icons rather than guessing.

Considered options: **per-caller measuring**, where each reader takes the half it believes is
current and fixes its own gap (the course of the four fixes this week). It leaves every reader free
to read the committed half mid-crossing and every pass free to disagree with the last, and a new
toggle must be mirrored in each assembly. Folding the slide into a travel-only pose was rejected for
the same reason: a travel path is a second derivation to forget.

Consequences: sizes at rest, the Looks, glass and One layout are unchanged. Five invariants are
asserted at the Dock geometry seam, one per bug class. The Robolectric bottom-stack cases move to
the Stack layout seam. The spec is #133.
