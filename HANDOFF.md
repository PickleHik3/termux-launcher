## Done
Key popup v2 (minimal): one filled glyph, 50 ms tap-or-swipe grace, anchor 6dp above the cap,
sizes -35%, solid `primary` + soft shadow, 120/100/60 ms motion. Veil, ring, halo and sub-label
deleted. Geometry/palette/controller tests rewritten; SPEC.md gained a `## v2 — minimal` section.
Acceptance suite green (KeyPopup* = 28 tests). Full suite green: 6250 tests, 0 failures.
Commit 30d9fb74 (+ this note).

## In progress
Nothing.

## Next
Waydroid gate (feel of size/position,
crossfade on a swipe, top row over the terminal, no flash on fast typing).

## Gotchas
`hasActivePopups()` now means "drawn", not "finger down"; `hasPendingPopups()` covers the grace
period. The timer lives on the overlay view's own main-looper Handler.
