# Widgets page controls and the display pane's glass — spec v1 (2026-09-10)

Two asks from the developer, recorded tersely so briefs can point at it. Decisions marked *chosen*
were taken by the orchestrator under the developer's stated intent; they are open to revision.

## Decisions

| # | Decision |
|---|---|
| D1 | **The Widgets page gets the Display page's border tab.** Tapping the 12dp border band of the widgets pane frame slides the same top-trailing peek tab out of the page edge (same geometry, motion, glyph style and haptic as `DisplayControlsView`), with two buttons: a settings cog and an edit (pencil) glyph. Tapping the border again, or anywhere outside, retracts it. The tab drawing is shared, not copied: one pane-controls view configured with its actions, used by both pages. |
| D2 | **Cog → Layout settings, scrolled to the widget grid.** Opens `LayoutPreferencesFragment` with the Home place preselected (whatever orientation the phone is in) and scrolls to the grid rows (`layout_grid_columns` / `layout_grid_rows`) once they are visible. Deep link is a `SettingsActivity` extra naming the place and the preference key to scroll to; no bespoke Intent per page. |
| D3 | **Pencil → widget edit mode** — the existing edit session (`WidgetPaneController.beginEditSession`, the menu's Edit widgets), with the same drag-to-move, resize handles, neighbour displacement and exits (Back, tap empty space) it has today. Nothing about the edit gestures changes. |
| D4 | **In edit mode a grid-size tab peeks from the same edge** (same tab style, top-trailing corner, replacing the cog/pencil tab while editing) reading `4 × 5` (columns × rows). It appears when edit mode starts and retracts when it ends. |
| D5 | **Tapping the grid tab opens a small Material popup** (`PopupWindow`, `colorSurfaceContainerHigh`, 16dp radius, elevation) with two vertical digit wheels, columns and rows, each dragged up or down to change by one per step (about 28dp per step, haptic tick per change), with `×` between them and a short label under each. Values apply live to the grid so the widgets reflow as the user drags; the popup closes on outside tap. *Chosen* bounds match the settings sliders: columns 2–8, rows 2–12. Never below 2, never above those maxima. Writes go through `PlaceLayoutStore.setWidgetColumns/Rows` for the Home place and current orientation, exactly as the Layout page does, so the Layout page and the tab always agree. |
| D6 | **The display pane wears the Canvas surface** — the same `PaneGlass` blur, opacity and grain the terminal panes and the Widgets page already take from `PaneSurfaceStyle`; no new surface slot, no new settings, so the Display place's look is already scopeable per place through the existing per-place look. The rim and corner mask stay. *Chosen*: reuse Canvas rather than add a Display slot, per "integrate into the wider theme, not a standalone thing". |
| D7 | **Where the glass is visible.** Behind the empty state (display off) and in the frame's band around the content, always. Through a running display only where the X surface has alpha: `LorieView` is a `SurfaceView`, so the glass shows through a transparent client only if the surface format is translucent and the server writes alpha. Phase 2 makes the surface translucent-capable if that is a one-line format change and measures nothing else; it does not touch the X server's compositing. Report honestly which of the two it achieved. |

## User-facing text

Tab glyphs have no labels. Popup: two short labels, "Columns" and "Rows". Layout page unchanged.

## Build plan

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 1 | `feat/widgets-border-controls` | D1–D5: shared pane-controls view, widgets border tap, settings deep link with scroll, edit-mode grid tab, digit-wheel popup, tests, docs line | — | unit tests (tap routing, wheel clamp policy, deep-link parsing); emulator screenshots: tab out, edit mode with grid tab, popup open |
| 2 | `feat/display-pane-glass` | D6–D7 | — | unit test that the display frame applies the pane glass like the widgets frame; emulator screenshot of the Display place, display off, with a blur/opacity/grain preset applied |

Both phases run in parallel from `dev`. Agents commit, never push, never touch pong or the
emulator; the orchestrator runs the visual gates and merges. Release-notes lines go in
`project-docs/release-notes-v0.2.40.md` when each lands.

## Risks

| Risk | Limit |
|---|---|
| Border tap steals a widget's edge touch | the band is outside the grid's content bounds, as on the Display page; tests pin it |
| Shrinking the grid live drops a widget | `LauncherWidgetRepository.setGridDefinition` already relocates what no longer fits; the wheel cannot go below 2 |
| Glass behind an opaque `SurfaceView` costs a blur for nothing | the backdrop is the shared wallpaper-blur cache; one more consumer, no per-frame work |
| Corner mask colour no longer matches a translucent frame | the mask keeps painting the wall-behind colour; visible only at the four corners while the display runs |
