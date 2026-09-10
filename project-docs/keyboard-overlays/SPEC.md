# Keyboard overlays — spec v1 (decided 2026-09-10)

Follow-up to `project-docs/keyboard-forms/SPEC.md`. Six things on the floating and split in-app
keyboard, decided with the developer on a review page; this is the terse record briefs point at.

## Decisions

| # | Decision |
|---|---|
| D1 | **Overlays are solid; the dock is glass.** Anything that lies over content — the floating keyboard on every place, the split halves, the docked keyboard in overlay mode (Display, Widgets), the mouse-mode touchpad — renders one opaque Material surface: no wallpaper crop, no frost, no glass slice, no rim stroke. The terminal's docked keyboard (resize mode) keeps the shared dock glass, unchanged. |
| D2 | **Surface role: `colorSurfaceContainerHigh`** (`?attr/termuxColorSurfacePanelHigh`), the role the drawer and popups use. Resolves through `LauncherSchemeTheme`, so every theme and scheme gets its own value. |
| D3 | **Overlays ignore the Keyboard surface's opacity slider** and render opaque; the slider still drives the docked keyboard. Side gap applies to every form. |
| D4 | **Floating card chrome.** Card and keyboard host share the D2 surface; the 1dp capsule ring is not drawn while floating (the docked Floating-dock-style capsule keeps its ring). Pill 52 × 3.2dp (floor 2px), handle row 22 → 18dp, host top margin 4 → 0dp and inner top padding 6 → 4dp while floating (sides stay 6dp). Elevation stays 8dp. |
| D5 | **Resize grip, bottom-left**, 28dp zone, two chevron lines in `termuxColorPrimary` mirrored from the floating terminal pane's. Drag scales width (right edge fixed) and row height. Width writes the existing floating-width value for the orientation; height gets a matching floating-height value per orientation (0.6–1.6, default 1.0). Width floor 240dp, ceiling the host width. Geometry pure in `FloatingKeyboardGeometry`. Pinch stays deferred. |
| D6 | **Split touchpad.** While mouse mode is on with a split keyboard the parting widens to 160dp (both halves shrink toward the edges) and the pad stands in it, a flush panel on the D2 surface, no stroke, the halves' radius, dot grid kept. Mouse mode off restores the user's gap. The whole-frame fallback remains only for a keyboard too narrow to part. The pad waits for the keyboard's first layout instead of fading the keys for a frame. |
| D7 | **A–Z bar letters** on horizontal bars are centred on the bar's centre line (full height, chin included), as the column already centres them; the scrub wave lift still rides on top. The bar's position is unchanged. |
| D8 | Reported during review, to investigate in phase 3: on the Display place in landscape, touch behaves like the trackpad mode regardless of the touch-mode setting and of mouse mode. |

## Build plan

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 1 | `feat/kb-float-chrome` | D2 + D4 for the floating card and its host; touchpad card solid (part of D1); D7 | — | unit tests; emulator screenshots portrait + landscape, terminal and Display places |
| 2 | `feat/kb-float-resize` | D5: grip, geometry + tests, height pref, settings row, docs | 1 | geometry tests; emulator drag; rotation keeps each orientation's size |
| 3 | `feat/kb-split-touchpad` | D6; D8 investigation | 1 | placement tests; pong check on the Display place (developer) |
| 4 | `feat/kb-overlay-material` | D1 + D3 for split halves and docked overlay mode; surface-editor preview follows | 1 | unit tests; emulator Display + Widgets places, keyboard up and down |

Phases 2, 3 and 4 run in parallel once 1 is merged. Agents commit, never push, never touch pong or
the emulator; the orchestrator runs the visual gates. Release notes go in
`project-docs/release-notes-v0.2.40.md` when each phase lands.

## Risks

| Risk | Limit |
|---|---|
| 3.2dp pill rounds to 2px on low densities | floor at 2px |
| Grip and pill touch zones | grip is in the content host's corner, pill in the handle row; they never overlap |
| Parting widen relayouts the keyboard on mouse-mode toggle | same relayout as the gap slider, keyboard's existing crossfade |
| Solid overlays under a light theme | verified per theme on the emulator in phase 1 |
| Existing installs | no stored value changes meaning; the height scale defaults to 1.0 |
