# Display place: full-screen mode with edge slide-outs (parked 2026-09-16)

Developer's ask, recorded before design: the Display place should be able to give the whole screen
to Linux apps — one GUI app at a time as today, or a full desktop environment the user runs
themselves — with no launcher chrome in the way. The chrome (extra keys, apps row, status bar,
A–Z index) comes back as slide-outs from the screen edges and hides again.

Groundwork the layout-freedom work already gives, to keep intact while fixing its defects:

- Every bar lives in the `EdgeStackView` of its edge (`place_edge_stack_top/bottom/left/right`).
  A full-screen mode is "every stack collapsed"; an edge swipe reveals that edge's stack over the
  display surface; a tap outside or a timeout collapses it. Do not special-case the bottom stack
  back into a fixed chain — that would make the bottom edge the one edge that cannot slide.
- `EdgeStackPolicy.contentInsets` is the one place the display surface learns what the bars
  claim; full-screen mode is `Insets.NONE` with the stacks drawn as overlays instead of insets.
- The Display place already has per-place layout (`PlaceLayout` for `PaneWallPage.DISPLAY`), so
  "full screen" is a per-place flag, not a global mode.

Open questions for the spec (later): how the in-app keyboard behaves in full screen (overlay
only?); whether the status bar's system-bar glass strip hides too (immersive mode); gesture
conflicts with the X11 pane (edge swipes vs. apps that use the screen edge); how a DE's own
panels coexist with the slide-outs; what the Layout editor shows for a full-screen place.

Status: parked until the layout-freedom defects (see `project-docs/layout-freedom/SPEC.md`) are
fixed and verified on the phone.
