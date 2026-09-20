# Appearance and Layout editors — spec v1 (decided 2026-09-15)

Supersedes "Layout page v2" and "Arranging inside the surface editor" in
`../per-place-layout/SPEC.md`. Vocabulary: `/CONTEXT.md`. Decision record:
`/docs/adr/0001-sizes-live-in-the-layout-store.md`. Grilling transcript: `.lavish/surface-editor-split*.html`.

## Problem statement

One overlay, the surface editor, edits both how a place looks and where its elements sit. Placement
happens by holding bars on the live screen with a finger over the thing being moved, the other
orientation can only be edited by rotating the phone, and Settings → Layout carries a second,
different miniature editor of its own. Users looking for "the glass" and users looking for "where the
dock goes" open the same crowded editor and find both.

## Solution

Two independent editors, each one button on every corner tab. **Appearance** edits the place's
surfaces: glass, opacity, blur, grain, corners, side gap, palette. **Layout** edits where the place's
elements sit and how big they are, on a miniature of the place that shows one orientation at a time
with a toggle to the other. Settings → Layout becomes a door into the Layout editor. In-place
placement on the live screen goes away.

## User stories

1. As a user, I want a Layout button on the corner tab of Home, Terminal and Display, so that I can change where things sit on the place I am looking at.
2. As a user, I want an Appearance button beside it, so that changing the glass never shows me placement controls.
3. As a user, I want the two buttons on a Terminal pane's corner tab too, so that every place behaves the same.
4. As a user, I want the Layout editor to show a miniature of the current place, so that I can see the whole screen while I move a bar.
5. As a user, I want to drag a bar on the miniature to an edge or into the hide tray, so that placement works the way it already does in Settings.
6. As a user, I want a Portrait / Landscape toggle above the miniature, so that I can lay out landscape without rotating the phone.
7. As a user, I want the live place behind the editor to follow each drop in the orientation I am in, so that I see the real result immediately.
8. As a user, I want edits to the other orientation to appear on the miniature only, so that nothing jumps behind the editor until I rotate.
9. As a user, I want rows beneath the miniature for keyboard form, keyboard on enter and keyboard mode, so that everything about a place's layout is in one editor.
10. As a user, I want rows for widget grid columns and rows on Home, so that the grid is set where the grid is shown.
11. As a user, I want sliders for dock height, keyboard height and keyboard chin in the Layout editor, so that sizes are set per place and per orientation like everything else there.
12. As a user, I want Done and Discard on the Layout editor, so that a wrong drag can be thrown away like a wrong opacity.
13. As a user, I want Back with unsaved changes to ask before discarding, matching Appearance.
14. As a user, I want the portrait miniature about half the screen tall, so that bars are comfortable to grip.
15. As a user, I want the landscape miniature to fill the width, so that it is not tiny.
16. As a user, I want Appearance to look like today's editor without the Place sections and the More… row, so that nothing I know moves.
17. As a user, I want Appearance's style-all action to stay, so that I can make every surface match in one tap.
18. As a user, I want Settings → Layout to offer Home, Terminal and Display, so that each opens the Layout editor on that place.
19. As a user, I want opening from Settings to land me on that place with the editor up, so that I see what I am editing.
20. As a user, I want the long-press menu to offer Appearance and Layout, so that I can find both without knowing about corner tabs.
21. As a user upgrading, I want my current dock height, keyboard height and chin carried over per place and orientation, so that nothing changes on update.
22. As a user upgrading, I want a per-place dock height override I set earlier to survive as that place's value in both orientations.
23. As a user, I want the help overlay and the first-boot tour to name the two buttons correctly, so that the tour is not lying.
24. As a user of a narrow split pane, I want the five corner-tab buttons to shrink rather than overflow, so that the tab stays usable.
25. As a developer, I want one shared home for corner-tab glyphs, so that adding a button is one edit.
26. As a developer, I want the Terminal corner tab on the same control view as the pages, so that there is one width rule and one glyph set.

## Implementation decisions

- **Two controllers, one Host pattern.** The surface editor controller becomes the Appearance
  editor: arrange mode, Place sections, hold-to-move overlay, drag grips and the More… row are
  removed. A new Layout editor controller sits beside it with a Host interface of the same shape
  (find views, current place, enter, close, unsaved-changes prompt). Both are overlays on the
  launcher activity so the live place stays visible behind them. Only one editor is open at a time.
- **Canvas.** The Layout editor hosts the existing miniature view, legend off, one orientation at a
  time, driven by the existing drop policy and drop callback. The orientation toggle above it
  re-binds the miniature; it does not touch the live place. Portrait frame height is about 55% of
  the screen; the landscape frame fills the width. The rows section beneath scrolls.
- **Rows.** The existing layout chooser row model supplies keyboard form, keyboard on enter,
  keyboard mode and grid counters, filtered to the current place and orientation. Three slider rows
  are added for dock height, keyboard height and keyboard chin, using the editor row kinds that
  Appearance already has. Side gap stays in Appearance as a surface property.
- **Sizes migrate into the layout store** (ADR 0001). Dock height, keyboard height scale and
  keyboard chin become per-place, per-orientation layout values. A versioned migration seeds each
  place and orientation from the value it resolved to before: keyboard height from the portrait or
  landscape global, chin from the global, dock height from the place's look override or Base. Dock
  height leaves the scopable look keys. Chrome geometry resolves the three through the layout store.
- **Commit.** Layout writes through while editing so the live place follows for the current
  orientation. On entry the arrangement snapshot is captured, extended to cover the three sizes;
  Discard, the revert glyph and the unsaved-changes dialog restore it. Dirtiness is the snapshot
  signature, as in Appearance.
- **Corner tabs.** Each frame's action list gains a Layout action next to the existing sliders
  action, which becomes Appearance. The Terminal pane's fixed four-slot overlay is replaced by the
  shared pane controls view and corner-tab geometry, then gains the fifth action; buttons scale
  down on narrow panes as the pages' tabs do. All corner-tab glyphs move into one shared glyph
  class. Layout uses the Nerd Font dashboard-grid glyph; Appearance keeps the sliders glyph.
- **Settings → Layout** becomes three rows, Home, Terminal, Display. Each finishes Settings and
  deep-links the launcher to that place with the Layout editor open, on the existing deep-link
  path. The twin miniatures and the element rows leave the page.
- **Long-press menu** offers Appearance and Layout, replacing its single surface editor entry.
- **Strings and docs.** The arrange hint and tray strings are removed. The help overlay
  inventory, Launcher Settings guide, first-boot tour notes, and the AGENTS.md glossary entry for
  "surface editor" are rewritten to the two editors. Product copy stays one plain sentence.

## Testing decisions

Good tests here drive a seam and assert what the user would see or what the store now holds, never
how a controller got there. JVM tests only, run with `./gradlew testDebugUnitTest`; a device check on
pong happens only when the developer asks.

- **Layout store**: migration seeds the sizes once per place and orientation from the prior values,
  including a per-place dock override; keys follow the existing scheme; clamping holds. Prior art:
  the store's existing test.
- **Arrangement snapshot**: capture and restore round-trip the sizes; signature changes when a size
  changes. Prior art: the snapshot test.
- **Appearance card plan**: no Place sections, no More… row, palette and look rows unchanged.
  Prior art: the card plan matrix test, shrunk.
- **Layout editor plan** (new): given a place and orientation, the rows offered and their values;
  a drop writes the edge or hides the bar; a slider write lands in the right orientation; toggling
  orientation changes what is shown, not what is stored; dirty and revert. Modelled on the card
  plan tests.
- **Corner tabs**: each frame's action list contains Appearance and Layout; geometry lays out five
  buttons and scales them under a narrow frame; a Terminal pane tap on the new slots reaches the
  right action. Prior art: the geometry test and the widgets frame tap test.
- **Settings door**: three rows, each firing the deep link for its place. Prior art: the layout
  preferences fragment test.
- **Live follow**: a drop in the current orientation re-renders chrome; a drop in the other does
  not. Tested at the chrome policy seam with the fake chrome surfaces.

## Out of scope

Status compact, keyboard-open memory and floating keyboard position stay unexposed. The landscape
status-column tap not raising its card is a separate fix. No new Appearance controls. No change to
the per-place look override model.

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| 1 | `feat/layout-sizes-store` | Sizes migrate into the layout store; snapshot covers them; chrome geometry reads them; dock height leaves the look keys | — |
| 2 | `feat/corner-tab-glyphs` | Shared glyph class; Terminal overlay on the shared control view; five-action geometry | — |
| 3 | `feat/appearance-editor` | Surface editor becomes Appearance: arrange mode, Place sections, grips, More… removed; strings dropped | 1 |
| 4 | `feat/layout-editor` | Layout editor controller and Host; miniature canvas, orientation toggle, rows, sliders, Done/Discard, live follow | 1, 3 |
| 5 | `feat/layout-doors` | Corner-tab Appearance and Layout actions on all three frames; long-press menu entries; Settings → Layout as three rows | 2, 4 |
| 6 | `feat/layout-docs` | Help overlay inventory, Launcher Settings guide, first-boot tour notes, AGENTS.md glossary, release notes | 5 |

Phases 1 and 2 run in parallel. Gate at every boundary: JVM unit tests green on the merged result;
device check on pong at the end of phase 5 when the developer asks.

## Further notes

The miniature renders any orientation regardless of the phone's, which is what makes the toggle
cheap. The Terminal migration in phase 2 is the only refactor without user-visible change; it is
there so phase 5 adds a button rather than a slot. The grilling pages under `.lavish/` hold the
question-by-question record and the rejected options.
