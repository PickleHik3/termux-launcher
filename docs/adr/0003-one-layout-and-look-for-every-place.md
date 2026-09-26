---
status: accepted
date: 2026-09-26
supersedes: the per-place scope of 0001 (sizes stay in the layout store; the store stops being keyed by place)
---

# One layout and one look for every place; places differ only in state

Home, Terminal and Display each kept their own layout (per orientation) and their own appearance
overrides. Switching places re-applied the arriving place's layout, look, status-bar height and
keyboard state at the moment the swipe was released. Measured on the HTC 5G Hub, that release
frame took 20–43 ms and skipped one or two vsyncs, and the dock visibly landed off and jumped.

We decided to keep one layout per orientation and one look, shared by all three places. Existing
setups migrate from each orientation's Terminal layout. What still differs per place is state,
never geometry or look:

- Whether the keyboard is up: Home is always closed; Terminal and Display each remember theirs.
- Minimal mode (Display and Terminal): the status bar shrinks to a strip, the apps bar and the
  keyboard go away, and the pane is maximised in either orientation.

Because the geometry is the same, the dock and keyboard can travel with the slide. Their positions
are a function of the wall's live offset, applied as transforms, and the terminal resizes once, at
settle.

The alternative was to keep per-place layouts and looks but pre-apply them before the slide
started. That keeps the flexibility, but it adds code and still pays a layout pass per switch,
and nobody was using the per-place differences deliberately.

Consequences: the Layout editor and the Appearance editor lose their place picker. Place-scoped
look keys (`PlaceLookPreferences.SCOPABLE`) and per-place layout entries in `PlaceLayoutStore`
need a versioned migration. The place-change handlers stop re-applying layout and look, and move
the remaining work to settle.
